package com.example.aiassistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

class AppAnalyzer(private val context: Context) {

    private val appCache = mutableListOf<InstalledAppInfo>()

    init {
        scanInstalledApps()
    }

    fun scanInstalledApps(): List<InstalledAppInfo> {
        appCache.clear()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
        for (info in resolveInfos) {
            val appName = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            appCache.add(InstalledAppInfo(appName, packageName))
        }
        Log.d("AppAnalyzer", "Indexed ${appCache.size} installed applications.")
        return appCache
    }

    fun findPackageForQuery(query: String): String? {
        if (appCache.isEmpty()) scanInstalledApps()
        val cleanQuery = query.lowercase().trim()

        // 1. Alias Dictionary for Instant Matching
        val aliases = mapOf(
            "insta" to "instagram",
            "ig" to "instagram",
            "yt" to "youtube",
            "fb" to "facebook",
            "wp" to "whatsapp",
            "wa" to "whatsapp",
            "snap" to "snapchat",
            "photos" to "gallery",
            "music" to "spotify",
            "mail" to "gmail"
        )
        val targetName = aliases[cleanQuery] ?: cleanQuery

        // 2. Exact Match
        appCache.firstOrNull { it.appName.lowercase() == targetName }?.let {
            return it.packageName
        }

        // 3. Prefix Match
        appCache.firstOrNull { it.appName.lowercase().startsWith(targetName) }?.let {
            return it.packageName
        }

        // 4. Substring Match
        appCache.firstOrNull { it.appName.lowercase().contains(targetName) }?.let {
            return it.packageName
        }

        // 5. Package Name Substring Match
        appCache.firstOrNull { it.packageName.lowercase().contains(targetName) }?.let {
            return it.packageName
        }

        return null
    }

    fun getLaunchIntentForAppName(appName: String): Intent? {
        val targetPackage = findPackageForQuery(appName) ?: return null
        return context.packageManager.getLaunchIntentForPackage(targetPackage)
    }

    fun getInstalledAppNamesSummary(): String {
        if (appCache.isEmpty()) scanInstalledApps()
        return appCache.map { it.appName }.sorted().distinct().take(40).joinToString(", ")
    }
}
