package com.ourmine.caregov.demo.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class UpdateRepository(private val context: Context) {
    suspend fun fetch(): AppUpdate = withContext(Dispatchers.IO) {
        val connection = openConnection(AppUpdate.MANIFEST_URL)
        try {
            checkResponse(connection)
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024 + 1)
                var length = 0
                while (length < buffer.size) {
                    val count = input.read(buffer, length, buffer.size - length)
                    if (count == -1) break
                    length += count
                }
                buffer.copyOf(length)
            }
            require(bytes.size <= 64 * 1024) { "업데이트 정보가 너무 큽니다." }
            AppUpdate.parse(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(update: AppUpdate, onProgress: suspend (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(AppUpdate.isReleaseUrl(update.apkUrl)) { "업데이트 파일 주소가 올바르지 않습니다." }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val partial = File(directory, "caregov-${update.versionCode}.part")
            val destination = File(directory, "caregov-${update.versionCode}.apk")
            val connection = openConnection(update.apkUrl)
            try {
                checkResponse(connection)
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastProgress = -1
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            received += count
                            require(received <= update.sizeBytes) { "업데이트 파일 크기가 일치하지 않습니다." }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = ((received * 100) / update.sizeBytes).toInt()
                            if (percent != lastProgress) {
                                onProgress(received.toFloat() / update.sizeBytes)
                                lastProgress = percent
                            }
                        }
                    }
                }
                require(received == update.sizeBytes) { "업데이트 다운로드가 완료되지 않았습니다." }
                require(digest.digest().toHex() == update.sha256) { "다운로드한 파일을 확인할 수 없습니다. 다시 시도해 주세요." }
                verifyPackage(partial, update)
                if (destination.exists()) check(destination.delete())
                check(partial.renameTo(destination)) { "업데이트 파일을 저장할 수 없습니다." }
                destination
            } finally {
                partial.delete()
                connection.disconnect()
            }
        }

    @Suppress("DEPRECATION")
    fun verifyPackage(file: File, update: AppUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val incoming = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("설치할 앱 파일을 확인할 수 없습니다.")
        require(incoming.packageName == context.packageName) { "다른 앱의 설치 파일입니다." }
        val code = if (Build.VERSION.SDK_INT >= 28) incoming.longVersionCode else incoming.versionCode.toLong()
        require(code == update.versionCode && code > installedVersionCode(installed)) { "설치할 앱 버전이 올바르지 않습니다." }
        require(incoming.applicationInfo?.minSdkVersion?.let { it <= Build.VERSION.SDK_INT } == true) {
            "이 휴대폰에서 사용할 수 없는 업데이트입니다."
        }
        val currentSigners = signers(installed)
        require(currentSigners.isNotEmpty() && currentSigners == signers(incoming)) {
            "기존 앱과 서명이 다른 파일은 설치할 수 없습니다."
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex()
        }.toSet()
    }

    private fun checkResponse(connection: HttpURLConnection) {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("업데이트 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        }
        require(connection.url.protocol == "https") { "안전한 연결을 확인할 수 없습니다." }
    }

    private fun openConnection(value: String): HttpURLConnection =
        (URL(value).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Caregov-Android")
        }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
