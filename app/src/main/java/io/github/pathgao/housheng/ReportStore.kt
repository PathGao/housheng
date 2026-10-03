package io.github.pathgao.housheng

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReportStore internal constructor(
    context: Context,
    databaseName: String,
    private val clock: () -> Long
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    constructor(context: Context) : this(context, "reports.db", System::currentTimeMillis)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notices (day TEXT NOT NULL, source TEXT NOT NULL, received INTEGER NOT NULL, matched INTEGER NOT NULL, requested INTEGER NOT NULL, protected INTEGER NOT NULL, PRIMARY KEY(day, source))")
        db.execSQL("CREATE TABLE seen (digest TEXT PRIMARY KEY, at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE service_events (id INTEGER PRIMARY KEY, at INTEGER NOT NULL, service TEXT NOT NULL, event TEXT NOT NULL)")
        db.execSQL("CREATE TABLE recording (test INTEGER PRIMARY KEY, first_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported report database migration: $oldVersion to $newVersion")
    }

    // eventId is the notification key plus postTime. Only its digest is persisted, never the input.
    fun recordNotification(source: String, eventId: String, outcome: NoticeOutcome, at: Long = clock()) {
        if (!AppCatalog.permits(source, "notifications") || eventId.isEmpty() || eventId.length > 16384) return
        val now = clock()
        if (at > now || at < startOfDay(day(now).minusDays(29))) return
        transaction { db ->
            prune(db, now)
            val digest = MessageDigest.getInstance("SHA-256").digest("$source\u0000$eventId".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val inserted = db.insertWithOnConflict("seen", null, ContentValues().apply {
                put("digest", digest); put("at", at)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) return@transaction
            val date = day(at).toString()
            db.execSQL("INSERT OR IGNORE INTO notices VALUES (?, ?, 0, 0, 0, 0)", arrayOf(date, source))
            val matched = outcome in setOf(NoticeOutcome.MATCHED, NoticeOutcome.REMOVAL_REQUESTED, NoticeOutcome.FAILED)
            db.execSQL("UPDATE notices SET received = received + 1, matched = matched + ?, requested = requested + ?, protected = protected + ? WHERE day = ? AND source = ?",
                arrayOf(if (matched) 1 else 0, if (outcome == NoticeOutcome.REMOVAL_REQUESTED) 1 else 0,
                    if (outcome == NoticeOutcome.PROTECTED) 1 else 0, date, source))
            val test = if (source == AppCatalog.FIXTURE) 1 else 0
            db.execSQL("INSERT OR IGNORE INTO recording VALUES (?, ?)", arrayOf(test, at))
            db.execSQL("UPDATE recording SET first_at = MIN(first_at, ?) WHERE test = ?", arrayOf(at, test))
            // Replay protection is limited to the newest 2048 callbacks within 24 hours.
            db.execSQL("DELETE FROM seen WHERE digest NOT IN (SELECT digest FROM seen ORDER BY at DESC, digest DESC LIMIT 2048)")
        }
    }

    fun recordService(service: String, event: String, at: Long = clock()) {
        if (service !in setOf("notifications", "pages") || event !in setOf("connected", "disconnected", "interrupted", "destroyed")) return
        val now = clock()
        if (at > now || at < startOfDay(day(now).minusDays(29))) return
        transaction { db ->
            prune(db, now)
            db.execSQL("INSERT INTO service_events(at, service, event) VALUES (?, ?, ?)", arrayOf(at, service, event))
            db.execSQL("DELETE FROM service_events WHERE id NOT IN (SELECT id FROM service_events ORDER BY at DESC, id DESC LIMIT 256)")
        }
    }

    fun snapshot(days: Int = 7, testData: Boolean = false): ReportSnapshot {
        require(days in 1..30) { "Report days must be between 1 and 30" }
        val now = clock()
        val start = day(now).minusDays(days - 1L)
        return transaction { db ->
            prune(db, now)
            val sourcePredicate = if (testData) "source = ?" else "source != ?"
            val summaries = mutableListOf<NoticeSummary>()
            db.rawQuery("SELECT source, SUM(received), SUM(matched), SUM(requested), SUM(protected) FROM notices WHERE day >= ? AND day <= ? AND $sourcePredicate GROUP BY source ORDER BY SUM(received) DESC, source",
                arrayOf(start.toString(), day(now).toString(), AppCatalog.FIXTURE)).use { cursor ->
                while (cursor.moveToNext()) {
                    val source = cursor.getString(0)
                    if (AppCatalog.permits(source, "notifications")) summaries += NoticeSummary(source, cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getInt(4))
                }
            }
            val firstAt = db.rawQuery("SELECT first_at FROM recording WHERE test = ?", arrayOf(if (testData) "1" else "0")).use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
            val events = mutableListOf<ServiceLogEntry>()
            if (!testData) db.rawQuery("SELECT at, service, event FROM service_events WHERE at >= ? AND at <= ? ORDER BY at DESC, id DESC LIMIT 50",
                arrayOf(startOfDay(start).toString(), now.toString())).use { cursor ->
                while (cursor.moveToNext()) events += ServiceLogEntry(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
            }
            ReportSnapshot(startOfDay(start), now, firstAt, summaries, events)
        }
    }

    fun clear() = transaction { db ->
        for (table in listOf("notices", "seen", "service_events", "recording")) db.delete(table, null, null)
    }

    private fun prune(db: SQLiteDatabase, now: Long) {
        val oldest = day(now).minusDays(29)
        db.delete("notices", "day < ?", arrayOf(oldest.toString()))
        db.delete("service_events", "at < ?", arrayOf(startOfDay(oldest).toString()))
        db.delete("seen", "at < ?", arrayOf((now - 86400000L).toString()))
    }

    private fun day(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun startOfDay(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun <T> transaction(action: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = action(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
}
