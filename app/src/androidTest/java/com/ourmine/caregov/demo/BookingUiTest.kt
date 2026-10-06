package com.ourmine.caregov.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun patientRequestSurvivesRecreation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bookingUi") == "true")
        val hospital = "한빛-${java.util.UUID.randomUUID().toString().take(4)}"
        compose.onNodeWithText("동행 신청").performScrollTo().performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithText("병원 이름").performTextInput(hospital)
        compose.onNodeWithText("진료과").performTextInput("내과")
        compose.onNodeWithText("만날 장소").performScrollTo().performTextInput("1층 로비")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("다음").performScrollTo().assertIsDisplayed().performClick()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.externalCacheDir, "request-review.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("신청 내용 확인").performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("동행 신청하기").performScrollTo().performClick()
        compose.onNodeWithText("접수 완료").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("$hospital\n내과").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("신청 수정").performScrollTo().performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithText("만날 장소").performScrollTo().performTextReplacement("2층 접수처")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("변경 내용 저장").performScrollTo().performClick()
        compose.onNodeWithText("2층 접수처").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("신청 취소").performScrollTo().performClick()
        compose.onNodeWithText("취소 확정").performClick()
        compose.onNodeWithText("취소된 신청입니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithContentDescription("예약 내역").performClick()
        compose.onNodeWithText("$hospital · 내과", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
    @Test fun accountMenusFollowLoggedInIdentity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bookingUi") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ServiceStore(context)
        try {
            store.accounts.forEach { account ->
                store.signOut()
                compose.activityRule.scenario.recreate()
                compose.onNodeWithText("휴대폰 번호").assertDoesNotExist()
                compose.onNodeWithText("사용자").performClick()
                compose.onNode(hasText("${account.role.label} · ${account.name}") and hasAnyAncestor(isPopup())).performScrollTo().assertIsDisplayed().performClick()
                compose.onNodeWithText("비밀번호").performTextInput("260401")
                compose.onNodeWithText("비밀번호").performImeAction()
                compose.onNodeWithText("${account.name}님,\n안녕하세요").performScrollTo().assertIsDisplayed()
                if (account.role == DemoRole.MANAGER || account.role == DemoRole.OPERATOR) {
                    compose.onNodeWithText("동행 신청").assertDoesNotExist()
                } else {
                    compose.onNodeWithText("동행 신청").performScrollTo().performClick()
                    compose.onNodeWithText(if (account.role == DemoRole.PATIENT) "본인 동행 신청" else "가족 동행 신청").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("뒤로").performClick()
                }
            }
        } finally {
            store.signIn("01000000001", "260401")
            compose.activityRule.scenario.recreate()
        }
    }
}
