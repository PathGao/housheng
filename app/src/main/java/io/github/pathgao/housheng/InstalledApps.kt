package io.github.pathgao.housheng

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

fun loadInstalledApps(context: Context): List<InstalledApp> {
    val manager = context.packageManager
    val now = System.currentTimeMillis()
    return manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName }.distinct().take(500).mapNotNull { source ->
            try {
                val info = manager.getPackageInfo(source, 0)
                val name = manager.getApplicationLabel(manager.getApplicationInfo(source, 0)).toString()
                    .replace(Regex("[\\p{Cntrl}\\s]+"), " ").take(80)
                InstalledApp(source, name, info.firstInstallTime)
            } catch (_: PackageManager.NameNotFoundException) { null }
        }.sortedWith(compareByDescending<InstalledApp> { installedInLast30Days(it, now) }.thenBy { it.name })
}
