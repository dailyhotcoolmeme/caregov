package com.ourmine.caregov.demo

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ourmine.caregov.demo.updates.UpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveUpdateTest {
    @Test
    fun downloadsAndVerifiesPublishedUpdate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveOta") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = UpdateRepository(context)
        val update = repository.fetch()
        assertTrue(update.versionCode > context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        val file = repository.download(update) {}
        assertEquals(update.sizeBytes, file.length())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() != -1) }
        var rejected = false
        try {
            repository.download(update.copy(sha256 = "0".repeat(64))) {}
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Mismatched hashes must be rejected", rejected)
    }
}
