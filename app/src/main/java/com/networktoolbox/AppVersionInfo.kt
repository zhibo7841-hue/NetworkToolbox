package com.networktoolbox

internal object AppVersionInfo {
    private const val FALLBACK_VERSION_NAME = "unknown"

    fun formatVersionName(versionName: String?): String {
        val displayVersion = versionName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_VERSION_NAME
        return "Version $displayVersion"
    }
}
