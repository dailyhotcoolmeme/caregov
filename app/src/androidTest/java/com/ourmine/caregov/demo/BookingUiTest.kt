package com.ourmine.caregov.demo

import androidx.compose.ui.test.*
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
        compose.onNodeWithText("병원 이름").performTextInput(hospital)
        compose.onNodeWithText("진료과").performTextInput("내과")
        compose.onNodeWithText("만날 장소").performScrollTo().performTextInput("1층 로비")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("동행 신청하기").performScrollTo().performClick()
        compose.onNodeWithText("접수 완료").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("$hospital\n내과").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("예약 내역").performClick()
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
                compose.onNodeWithText("휴대폰 번호").performTextInput(account.phone)
                compose.onNodeWithText("비밀번호").performTextInput("482619")
                compose.onNode(hasText("로그인") and hasClickAction()).performScrollTo().performClick()
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
            store.signIn("01000000001", "482619")
            compose.activityRule.scenario.recreate()
        }
    }
}
