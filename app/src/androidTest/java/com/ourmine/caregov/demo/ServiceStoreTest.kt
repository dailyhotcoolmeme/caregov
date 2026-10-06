package com.ourmine.caregov.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ServiceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block: (ServiceStore, String) -> Unit) {
        val name = "test-${UUID.randomUUID()}"
        try { block(ServiceStore(context, name), name) }
        finally { context.deleteSharedPreferences(name) }
    }
    @Test fun loginAndLogoutPersist() = fixture { store, name ->
        assertNull(store.signIn("01000000004", "wrong"))
        assertEquals(DemoRole.OPERATOR, store.signIn("010-0000-0004", "482619")?.role)
        assertEquals(DemoRole.OPERATOR, ServiceStore(context, name).account()?.role)
        store.signOut(); assertNull(ServiceStore(context, name).account())
    }
    @Test fun selfBookingPersistsAndRemainsUnassigned() = fixture { store, name ->
        val patient = store.accounts.first()
        val booking = create(store, patient)
        assertEquals("접수 완료", booking.status); assertEquals("", booking.manager)
        assertEquals(booking, ServiceStore(context, name).bookings().last())
        assertTrue(store.visible(patient, store.bookings()).contains(booking))
        assertFalse(store.visible(store.accounts[2], store.bookings()).contains(booking))
        assertTrue(store.visible(store.accounts[3], store.bookings()).contains(booking))
    }
    @Test fun proxyBookingIsVisibleToLinkedPatient() = fixture { store, _ ->
        val booking = create(store, store.accounts[1])
        assertTrue(store.visible(store.accounts[0], store.bookings()).contains(booking))
        assertTrue(store.visible(store.accounts[1], store.bookings()).contains(booking))
    }
    @Test fun managersCannotCreatePatientRequests() = fixture { store, _ ->
        assertThrows(IllegalArgumentException::class.java) { create(store, store.accounts[2]) }
    }
    @Test fun invalidDatesDoNotWriteBookings() = fixture { store, _ ->
        assertThrows(IllegalArgumentException::class.java) {
            store.create(store.accounts[0], draft(store.accounts[0]).copy(date = "2020-01-01"))
        }
        assertEquals(1, store.bookings().size)
    }
    @Test fun modificationAndCancellationPersist() = fixture { store, name ->
        val account = store.accounts[0]
        val original = create(store, account)
        val changed = store.update(account, original.id, 0, draft(account).copy(hospital = "한빛병원"))
        assertEquals(1, changed.revision)
        assertEquals(changed, ServiceStore(context, name).bookings().last())
        assertThrows(IllegalArgumentException::class.java) { store.update(account, original.id, 0, draft(account)) }
        assertThrows(IllegalArgumentException::class.java) { store.cancel(store.accounts[1], original.id, 1, "일정 변경") }
        val cancelled = store.cancel(account, original.id, 1, "일정 변경")
        assertEquals("신청 취소", cancelled.status)
        assertEquals("일정 변경", cancelled.cancellationReason)
        assertEquals(cancelled, ServiceStore(context, name).bookings().last())
        assertFalse(store.canModify(account, cancelled))
        assertThrows(IllegalArgumentException::class.java) { store.cancel(account, original.id, 2, "일정 변경") }
        assertFalse(store.canModify(account, store.bookings().first()))
    }
    @Test fun sharingScopesControlGuardianAccess() = fixture { store, _ ->
        val account = store.accounts[0]
        SharingScope.entries.forEach { scope ->
            val request = draft(account)
            val booking = store.create(account, request.copy(options = request.options.copy(sharing = scope)))
            assertEquals(scope != SharingScope.NONE, store.visible(store.accounts[1], listOf(booking)).isNotEmpty())
            assertEquals(scope == SharingScope.RESULTS, store.canReadReport(store.accounts[1], booking))
        }
    }
    @Test fun unrelatedProxyPatientIsNotLinkedByName() = fixture { store, _ ->
        val account = store.accounts[1]
        val request = draft(account)
        val booking = store.create(account, request.copy(options = request.options.copy(patientAccountId = "")))
        assertTrue(store.visible(store.accounts[0], listOf(booking)).isEmpty())
        assertFalse(store.canModify(store.accounts[0], booking))
    }
    @Test fun contactConsentAndQuoteAreValidated() = fixture { store, _ ->
        val account = store.accounts[0]
        val request = draft(account)
        assertThrows(IllegalArgumentException::class.java) { store.create(account, request.copy(options = request.options.copy(consent = false))) }
        assertThrows(IllegalArgumentException::class.java) { store.create(account, request.copy(options = request.options.copy(patientPhone = "123"))) }
        val booking = store.create(account, request.copy(options = request.options.copy(hours = 3, estimatedWon = 1)))
        assertEquals(60_000L, booking.options.estimatedWon)
        assertEquals(20_000L, ServicePricing.estimate(1))
        assertThrows(IllegalArgumentException::class.java) { ServicePricing.estimate(9) }
    }
    @Test fun legacyRecordsRemainReadable() = fixture { _, name ->
        val preferences = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        val rows = org.json.JSONArray(preferences.getString("bookings", "[]"))
        rows.getJSONObject(0).remove("options")
        rows.getJSONObject(0).remove("revision")
        rows.getJSONObject(0).remove("workflow")
        preferences.edit().putString("bookings", rows.toString()).commit()
        val booking = ServiceStore(context, name).bookings().first()
        assertEquals("CG-1001", booking.id)
        assertEquals("예약 확정", booking.status)
        assertNull(booking.options.estimatedWon)
        assertEquals(0, booking.revision)
    }
    private fun create(store: ServiceStore, account: ServiceAccount) = store.create(account, draft(account))
    private fun draft(account: ServiceAccount) = BookingDraft("김영희", "서울의료원", "내과", LocalDate.now().plusDays(1).toString(),
        "10:00", "1층 로비", "보행 보조", "요청사항", RequestOptions("01000000001", if (account.role == DemoRole.GUARDIAN) "어머니" else "본인",
            if (account.role == DemoRole.GUARDIAN) account.phone else "", patientAccountId = "patient", consent = true))
}
