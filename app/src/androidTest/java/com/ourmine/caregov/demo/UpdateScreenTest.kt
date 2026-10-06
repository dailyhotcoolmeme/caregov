package com.ourmine.caregov.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun installedVersionIsCurrent() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("currentOta") == "true")
        compose.onNodeWithContentDescription("앱 업데이트 확인").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("최신 버전을 사용하고 있습니다.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("현재 버전 ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("최신 버전을 사용하고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
    }
}
