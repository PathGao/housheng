package io.github.pathgao.housheng

enum class NoticeOutcome { ALLOWED, PROTECTED, MATCHED, REMOVAL_REQUESTED, FAILED }

data class NoticeSummary(
    val source: String,
    val received: Int,
    val matched: Int,
    val removalRequested: Int,
    val `protected`: Int
)

data class ServiceLogEntry(val at: Long, val service: String, val event: String)

data class ReportSnapshot(
    val startAt: Long,
    val endAt: Long,
    val firstRecordedAt: Long?,
    val notifications: List<NoticeSummary>,
    val serviceEvents: List<ServiceLogEntry>
)
