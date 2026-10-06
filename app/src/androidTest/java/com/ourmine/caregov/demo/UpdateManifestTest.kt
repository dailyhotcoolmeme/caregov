package com.ourmine.caregov.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ourmine.caregov.demo.updates.AppUpdate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateManifestTest {
    @Test
    fun acceptsMatchingReleaseManifest() {
        val update = AppUpdate.parse(manifest().toString())
        assertEquals(3L, update.versionCode)
        assertEquals("0.2.1", update.versionName)
    }

    @Test
    fun acceptsOnlyScopedR2DownloadPaths() {
        val url = "https://caregov-ota.dailyhotcoolmeme.workers.dev/apk/caregov-0.3.1-abcdef012345.apk"
        assertEquals(url, AppUpdate.parse(manifest().put("apkUrl", url).toString()).apkUrl)
        assertRejected("apkUrl", "https://caregov-ota.dailyhotcoolmeme.workers.dev/apk/other.apk")
        assertRejected("apkUrl", "https://ootd-media.dailyhotcoolmeme.workers.dev/apk/caregov.apk")
        assertRejected("apkUrl", url + "?token=invalid")
    }

    @Test
    fun rejectsOtherPackagesAndUntrustedDownloads() {
        assertRejected("applicationId", "com.example.other")
        assertRejected("apkUrl", "http://github.com/dailyhotcoolmeme/caregov/releases/download/v3/demo.apk")
        assertRejected("apkUrl", "https://github.com/other/project/releases/download/v3/demo.apk")
        assertRejected("apkUrl", "https://github.com@evil.example/dailyhotcoolmeme/caregov/releases/download/v3/demo.apk")
        assertRejected("apkUrl", "https://github.com/dailyhotcoolmeme/caregov/releases/download/../demo.apk")
    }

    @Test
    fun rejectsMissingIntegrityAndInvalidVersions() {
        assertRejected("sha256", "invalid")
        assertRejected("sizeBytes", 0)
        assertRejected("sizeBytes", AppUpdate.MAX_APK_BYTES + 1)
        assertRejected("versionCode", -1)
        assertRejected("schemaVersion", 2)
    }

    private fun assertRejected(field: String, value: Any) {
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdate.parse(manifest().put(field, value).toString())
        }
    }

    private fun manifest() = JSONObject()
        .put("schemaVersion", 1)
        .put("applicationId", AppUpdate.APPLICATION_ID)
        .put("versionCode", 3)
        .put("versionName", "0.2.1")
        .put("apkUrl", "https://github.com/dailyhotcoolmeme/caregov/releases/download/android-v0.2.1/caregov-demo.apk")
        .put("sha256", "a".repeat(64))
        .put("sizeBytes", 1024)
}
