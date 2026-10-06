package com.ourmine.caregov.demo.updates

import org.json.JSONObject
import java.net.URI

data class AppUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseNotes: String,
) {
    companion object {
        const val MANIFEST_URL = "https://raw.githubusercontent.com/dailyhotcoolmeme/caregov/main/updates/android.json"
        const val APPLICATION_ID = "com.ourmine.caregov.demo"
        const val MAX_APK_BYTES = 150L * 1024 * 1024

        fun parse(json: String): AppUpdate {
            val data = JSONObject(json)
            require(data.getInt("schemaVersion") == 1) { "지원하지 않는 업데이트 정보입니다." }
            require(data.getString("applicationId") == APPLICATION_ID) { "다른 앱의 업데이트 정보입니다." }
            val update = AppUpdate(
                versionCode = data.getLong("versionCode"),
                versionName = data.getString("versionName"),
                apkUrl = data.getString("apkUrl"),
                sha256 = data.getString("sha256").lowercase(),
                sizeBytes = data.getLong("sizeBytes"),
                releaseNotes = data.optString("releaseNotes").take(1000),
            )
            require(update.versionCode > 0 && update.versionName.isNotBlank()) { "버전 정보가 올바르지 않습니다." }
            require(update.sha256.matches(Regex("[a-f0-9]{64}"))) { "파일 확인 정보가 올바르지 않습니다." }
            require(update.sizeBytes in 1..MAX_APK_BYTES) { "업데이트 파일 크기가 올바르지 않습니다." }
            require(isReleaseUrl(update.apkUrl)) { "업데이트 파일 주소가 올바르지 않습니다." }
            return update
        }

        fun isReleaseUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "github.com" &&
                uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null &&
                uri.path.startsWith("/dailyhotcoolmeme/caregov/releases/download/") &&
                uri.path.endsWith(".apk") && !uri.path.contains("..")
        }.getOrDefault(false)
    }
}
