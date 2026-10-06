package com.ourmine.caregov.demo

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkflowUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store get() = ServiceStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun login(phone: String) {
        store.signOut()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("휴대폰 번호").performTextInput(phone)
        compose.onNodeWithText("비밀번호").performTextInput("482619")
        compose.onNode(hasText("로그인") and hasClickAction()).performScrollTo().performClick()
    }
    private fun open(id: String, tab: String) {
        compose.onNodeWithContentDescription(tab).performClick()
        compose.onNodeWithContentDescription("예약 $id").performScrollTo().performClick()
    }
    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(compose.activity.externalCacheDir, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun assignmentProgressReportAndSettlementAcrossAccounts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("workflowUi") == "true")
        val hospital = "동행-${UUID.randomUUID().toString().take(4)}"
        val booking = store.create(store.accounts[0], BookingDraft("김영희", hospital, "내과", LocalDate.now().plusDays(2).toString(),
            "10:00", "1층 로비", "도보 이동", "접수 창구 동행", RequestOptions(patientPhone = "01000000001", sharing = SharingScope.RESULTS, consent = true)))
        try {
            login("01000000004")
            open(booking.id, "예약 관리")
            compose.onNodeWithText("매니저 배정").performScrollTo().performClick()
            compose.onNodeWithText("박서연").performClick()
            compose.onNodeWithText("배정").performClick()
            compose.onNodeWithText("배정 대기").performScrollTo().assertIsDisplayed()
            capture("operator-assignment.png")

            login("01000000003")
            open(booking.id, "내 일정")
            compose.onNodeWithText("배정 수락").performScrollTo().performClick()
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithText("예약 확정").performScrollTo().assertIsDisplayed()
            listOf("동행 시작", "병원 도착 확인", "진료 완료 확인", "귀가 완료 확인").forEach { action ->
                compose.onNodeWithText(action).performScrollTo().performClick()
                compose.onNodeWithText("확인").performClick()
            }
            compose.onNodeWithText("동행 내용").performScrollTo().performTextInput("진료 접수와 귀가를 함께했습니다.")
            compose.onNodeWithText("병원 안내 사항 (선택)").performScrollTo().performTextInput("다음 방문 일정은 병원에 확인했습니다.")
            compose.onNodeWithText("실제 이용 시간 (분)").performScrollTo().performTextReplacement("125")
            compose.onNodeWithText("동행 완료 및 결과 등록").performScrollTo().performClick()
            compose.onNode(hasText("결과 등록") and hasClickAction()).performClick()
            compose.onNodeWithText("동행 완료").performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("진료 접수와 귀가를 함께했습니다.").performScrollTo().assertIsDisplayed()
            capture("manager-report.png")

            login("01000000002")
            open(booking.id, "예약 내역")
            compose.onNodeWithText("진료 접수와 귀가를 함께했습니다.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("이용 후기 (선택)").performScrollTo()
            compose.onNodeWithText("4점").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("4점").assertIsSelected()
            compose.onNodeWithText("이용 후기 (선택)").performScrollTo().performTextInput("편안하게 다녀왔습니다.")
            compose.onNodeWithText("4점").assertIsSelected()
            compose.onNodeWithText("평가 등록").performScrollTo().performClick()
            compose.onNodeWithText("4 / 5").performScrollTo().assertIsDisplayed()

            login("01000000004")
            open(booking.id, "예약 관리")
            compose.onNodeWithText("정산 확정").performScrollTo().performClick()
            compose.onNodeWithText("확정").performClick()
            assertEquals(4, store.bookings().first { it.id == booking.id }.workflow.rating)
            compose.onNodeWithContentDescription("뒤로").performClick()
            compose.onNodeWithContentDescription("정산 관리").performClick()
            compose.onNode(hasText("정산 확정") and hasClickAction()).performClick()
            compose.onNode(hasText("${booking.date} · 김영희") and hasText("박서연 · $hospital")).performScrollTo().assertIsDisplayed()
            capture("settlement-history.png")

            login("01000000001")
            open(booking.id, "예약 내역")
            compose.onNodeWithText("공유 범위 변경").performScrollTo().performClick()
            compose.onNodeWithText("진행 상황만").performClick()
            compose.onNodeWithText("공유 설정 저장").performClick()
            login("01000000002")
            open(booking.id, "예약 내역")
            compose.onNodeWithText("결과 정보가 공유되지 않았습니다.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("진료 접수와 귀가를 함께했습니다.").assertDoesNotExist()
            compose.onNodeWithText("접수 창구 동행").assertDoesNotExist()
        } finally {
            store.signIn("01000000001", "482619")
            compose.activityRule.scenario.recreate()
        }
    }
}
