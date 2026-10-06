package com.ourmine.caregov.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VisitWorkflowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block: (ServiceStore, String) -> Unit) {
        val name = "workflow-${UUID.randomUUID()}"
        try { block(ServiceStore(context, name), name) } finally { context.deleteSharedPreferences(name) }
    }
    private fun request(store: ServiceStore, service: VisitService = VisitService.OUTPATIENT) = store.create(store.accounts[0],
        BookingDraft("김영희", "한빛병원", "내과", LocalDate.now().plusDays(2).toString(), "10:00", "1층", "도보 이동", "",
            RequestOptions(patientPhone = "01000000001", sharing = SharingScope.RESULTS, service = service, consent = true)))
    private fun accepted(store: ServiceStore, service: VisitService = VisitService.OUTPATIENT): Booking {
        val booking = request(store, service)
        val assigned = store.assign(store.accounts[3], booking.id, booking.revision, "manager")
        return store.respond(store.accounts[2], assigned.id, assigned.revision, true)
    }
    private fun finish(store: ServiceStore, service: VisitService = VisitService.OUTPATIENT): Booking {
        var booking = accepted(store, service)
        while (VisitSteps.next(booking) != null) booking = store.advance(store.accounts[2], booking.id, booking.revision, booking.status)
        return store.complete(store.accounts[2], booking.id, booking.revision, VisitReport("접수와 진료, 귀가를 함께했습니다.", minutes = 125))
    }
    @Test fun entireVisitAndSettlementPersist() = fixture { store, name ->
        val complete = finish(store)
        assertEquals("동행 완료", complete.status)
        assertEquals(50_000L, complete.workflow.report!!.feeWon)
        assertEquals(40_000L, complete.workflow.report!!.managerWon)
        assertEquals(complete, ServiceStore(context, name).bookings().last())
        val settled = store.confirmSettlement(store.accounts[3], complete.id, complete.revision)
        assertTrue(settled.workflow.settlementAt.isNotBlank())
        assertThrows(IllegalArgumentException::class.java) { store.confirmSettlement(store.accounts[3], settled.id, settled.revision) }
        val reviewed = store.review(store.accounts[0], settled.id, settled.revision, 4, "친절했어요")
        assertEquals(4, reviewed.workflow.rating)
        assertEquals(reviewed, ServiceStore(context, name).bookings().last())
        assertThrows(IllegalArgumentException::class.java) { store.review(store.accounts[0], reviewed.id, reviewed.revision, 5, "") }
    }
    @Test fun assignmentRejectsOverlapAndAllowsReassignment() = fixture { store, _ ->
        val first = accepted(store)
        val second = request(store)
        assertThrows(IllegalArgumentException::class.java) { store.assign(store.accounts[3], second.id, second.revision, "manager") }
        store.assign(store.accounts[3], second.id, second.revision, "manager2")
        assertThrows(IllegalArgumentException::class.java) { store.assign(store.accounts[3], first.id, first.revision, "manager2") }
        val moved = store.reschedule(store.accounts[3], first.id, first.revision, LocalDate.now().plusDays(3).toString(), "10:00", "일정 변경")
        val changed = store.assign(store.accounts[3], moved.id, moved.revision, "manager2")
        assertEquals("최지은", changed.manager)
        assertEquals("배정 대기", changed.status)
    }
    @Test fun rejectionReturnsRequestToIntake() = fixture { store, _ ->
        val booking = request(store)
        val assigned = store.assign(store.accounts[3], booking.id, booking.revision, "manager")
        assertThrows(IllegalArgumentException::class.java) { store.respond(store.accounts[2], assigned.id, assigned.revision, false) }
        val declined = store.respond(store.accounts[2], assigned.id, assigned.revision, false, "일정 조정 필요")
        assertEquals("접수 완료", declined.status)
        assertEquals("", declined.manager)
        assertTrue(store.visible(store.accounts[2], listOf(declined)).isEmpty())
        assertTrue(declined.workflow.events.last().title.contains("일정 조정 필요"))
    }
    @Test fun reschedulingRequiresAnotherAcceptance() = fixture { store, _ ->
        val booking = accepted(store)
        val changed = store.reschedule(store.accounts[3], booking.id, booking.revision, LocalDate.now().plusDays(3).toString(), "12:00", "병원 예약 변경")
        assertEquals("배정 대기", changed.status)
        assertThrows(IllegalArgumentException::class.java) { store.advance(store.accounts[2], changed.id, changed.revision, changed.status) }
        val accepted = store.respond(store.accounts[2], changed.id, changed.revision, true)
        val started = store.advance(store.accounts[2], accepted.id, accepted.revision, accepted.status)
        assertThrows(IllegalArgumentException::class.java) { store.operatorCancel(store.accounts[3], started.id, started.revision, "취소") }
        assertThrows(IllegalArgumentException::class.java) { store.reschedule(store.accounts[3], started.id, started.revision, changed.date, "13:00", "변경") }
    }
    @Test fun unauthorizedAndStaleWritesFailClosed() = fixture { store, _ ->
        val booking = request(store)
        assertThrows(IllegalArgumentException::class.java) { store.assign(store.accounts[0], booking.id, 0, "manager") }
        val assigned = store.assign(store.accounts[3], booking.id, 0, "manager")
        assertThrows(IllegalArgumentException::class.java) { store.respond(store.accounts[4], assigned.id, assigned.revision, true) }
        assertThrows(IllegalArgumentException::class.java) { store.respond(store.accounts[2], assigned.id, 0, true) }
        assertThrows(IllegalArgumentException::class.java) { store.complete(store.accounts[2], assigned.id, assigned.revision, VisitReport("내용")) }
        assertThrows(IllegalArgumentException::class.java) { store.confirmSettlement(store.accounts[3], assigned.id, assigned.revision) }
        assertEquals(assigned, store.bookings().last())
    }
    @Test fun resultSharingCanBeRevokedAfterCompletion() = fixture { store, _ ->
        val booking = finish(store)
        assertTrue(store.canReadReport(store.accounts[1], booking))
        val progress = store.changeSharing(store.accounts[0], booking.id, booking.revision, SharingScope.PROGRESS)
        assertFalse(store.canReadReport(store.accounts[1], progress))
        assertThrows(IllegalArgumentException::class.java) { store.review(store.accounts[1], progress.id, progress.revision, 5, "") }
        assertThrows(IllegalArgumentException::class.java) { store.changeSharing(store.accounts[1], progress.id, progress.revision, SharingScope.RESULTS) }
        val hidden = store.changeSharing(store.accounts[0], progress.id, progress.revision, SharingScope.NONE)
        assertTrue(store.visible(store.accounts[1], listOf(hidden)).isEmpty())
        assertTrue(store.canReadReport(store.accounts[0], hidden))
    }
    @Test fun returnHomeOmitsHospitalSteps() = fixture { store, _ ->
        val booking = finish(store, VisitService.RETURN_HOME)
        assertFalse(booking.workflow.events.any { it.title == "병원 도착" || it.title == "진료 완료" })
        assertTrue(booking.workflow.events.any { it.title == "귀가 완료" })
    }
    @Test fun examinationUsesExamStatus() = fixture { store, _ ->
        val booking = finish(store, VisitService.EXAMINATION)
        assertTrue(booking.workflow.events.any { it.title == "검사 완료" })
        assertFalse(booking.workflow.events.any { it.title == "진료 완료" })
    }
    @Test fun settlementLimitsAndRounding() {
        assertEquals(20_000L, SettlementPricing.fee(1))
        assertEquals(20_000L, SettlementPricing.fee(60))
        assertEquals(30_000L, SettlementPricing.fee(61))
        assertEquals(160_000L, SettlementPricing.fee(480))
        assertThrows(IllegalArgumentException::class.java) { SettlementPricing.fee(0) }
        assertThrows(IllegalArgumentException::class.java) { SettlementPricing.fee(481) }
    }
}
