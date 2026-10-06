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
            store.create(store.accounts[0], "김영희", "서울의료원", "내과", "2020-01-01", "10:00", "로비", "도보 이동", "")
        }
        assertEquals(1, store.bookings().size)
    }
    private fun create(store: ServiceStore, account: ServiceAccount) = store.create(account,
        "김영희", "서울의료원", "내과", LocalDate.now().plusDays(1).toString(), "10:00", "1층 로비", "보행 보조", "요청사항")
}
