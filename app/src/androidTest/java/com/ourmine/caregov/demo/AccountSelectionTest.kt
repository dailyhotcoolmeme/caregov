package com.ourmine.caregov.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun switchesUsersWithoutLoggingOutAndRejectsOldPassword() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("loginUi") == "true")
        val store = ServiceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.signOut()
        compose.activityRule.scenario.recreate()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.externalCacheDir, "user-selection-login.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        store.signIn("01000000001", "260401")
        compose.activityRule.scenario.recreate()
        val originalIds = store.bookings().map { it.id }
        fun select(account: ServiceAccount) {
            compose.onNodeWithContentDescription("내 정보").performClick()
            compose.onNode(hasText("사용자 변경") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("사용자").performClick()
            compose.onNode(hasText("${account.role.label} · ${account.name}") and hasAnyAncestor(isPopup())).performScrollTo().assertIsDisplayed().performClick()
        }
        try {
            select(store.accounts[3])
            compose.onNodeWithText("비밀번호").performTextInput("482619")
            compose.onNodeWithText("변경").performClick()
            compose.onNodeWithText("비밀번호를 확인해 주세요.").assertIsDisplayed()
            assertEquals("patient", store.account()?.id)
            compose.onNodeWithText("비밀번호").performTextReplacement("260401")
            compose.onNodeWithText("변경").performClick()
            compose.onNodeWithText("운영팀님,\n안녕하세요").performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            assertEquals("operator", store.account()?.id)
            select(store.accounts[1])
            compose.onNodeWithText("비밀번호").performTextInput("260401")
            compose.onNodeWithText("변경").performClick()
            compose.onNodeWithText("이준호님,\n안녕하세요").performScrollTo().assertIsDisplayed()
            select(store.accounts[2])
            compose.onNodeWithText("취소").performClick()
            assertEquals("guardian", store.account()?.id)
            assertEquals(originalIds, store.bookings().map { it.id })
        } finally {
            store.signIn("01000000001", "260401")
            compose.activityRule.scenario.recreate()
        }
    }
}
