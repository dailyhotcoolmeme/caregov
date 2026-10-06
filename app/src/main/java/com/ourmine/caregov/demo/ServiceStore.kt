package com.ourmine.caregov.demo

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class ServiceAccount(val id: String, val name: String, val phone: String, val role: DemoRole)
data class Booking(
    val id: String, val requesterId: String, val patient: String, val hospital: String,
    val department: String, val date: String, val time: String, val meeting: String,
    val support: String, val note: String, val manager: String = "", val status: String = "접수 완료",
    val patientId: String = "",
)

class ServiceStore(context: Context, storageName: String = "service_records") {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    val accounts = listOf(
        ServiceAccount("patient", "김영희", "01000000001", DemoRole.PATIENT),
        ServiceAccount("guardian", "이준호", "01000000002", DemoRole.GUARDIAN),
        ServiceAccount("manager", "박서연", "01000000003", DemoRole.MANAGER),
        ServiceAccount("operator", "운영팀", "01000000004", DemoRole.OPERATOR),
    )
    init { if (!preferences.contains("bookings")) save(listOf(
        Booking("CG-1001", "patient", "김영희", "서울의료원", "내과", LocalDate.now().toString(),
            "10:00", "서울의료원 1층 로비", "도보 이동", "접수 창구까지 함께 이동해 주세요.", "박서연", "예약 확정", "patient"),
    )) }
    fun account(): ServiceAccount? = if (preferences.getBoolean("signed_out", false)) null
        else accounts.firstOrNull { it.id == preferences.getString("account", "patient") }

    fun signIn(phone: String, pin: String): ServiceAccount? {
        // Local fixture identities only; not server authentication.
        val account = accounts.firstOrNull { it.phone == phone.filter(Char::isDigit) }
        if (account == null || pin != "482619") return null
        preferences.edit { putString("account", account.id); putBoolean("signed_out", false) }
        return account
    }
    fun signOut() { preferences.edit { putBoolean("signed_out", true) } }
    fun bookings(): List<Booking> {
        val raw = requireNotNull(preferences.getString("bookings", null))
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val r = array.getJSONObject(index)
            Booking(r.getString("id"), r.getString("requesterId"), r.getString("patient"),
                r.getString("hospital"), r.getString("department"), r.getString("date"), r.getString("time"),
                r.getString("meeting"), r.getString("support"), r.getString("note"), r.optString("manager"), r.getString("status"), r.optString("patientId"))
        }
    }
    fun visible(account: ServiceAccount, bookings: List<Booking>): List<Booking> = bookings.filter {
        when (account.role) {
            DemoRole.PATIENT -> it.requesterId == account.id || it.patientId == account.id
            DemoRole.GUARDIAN -> it.requesterId == account.id || it.id == "CG-1001"
            DemoRole.MANAGER -> it.manager == account.name
            DemoRole.OPERATOR -> true
        }
    }
    fun create(account: ServiceAccount, patient: String, hospital: String, department: String,
        date: String, time: String, meeting: String, support: String, note: String): Booking {
        require(account.role == DemoRole.PATIENT || account.role == DemoRole.GUARDIAN)
        require(patient.isNotBlank() && hospital.isNotBlank() && department.isNotBlank() && meeting.isNotBlank())
        require(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).isAfter(LocalDateTime.now())) { "방문 일시는 현재 이후로 선택해 주세요." }
        val booking = Booking("CG-${UUID.randomUUID().toString().take(8).uppercase()}", account.id,
            patient.trim(), hospital.trim(), department.trim(), date, time, meeting.trim(), support, note.trim(),
            patientId = if (account.role == DemoRole.PATIENT) account.id else if (account.id == "guardian" && patient.trim() == "김영희") "patient" else "")
        save(bookings() + booking)
        return booking
    }
    private fun save(bookings: List<Booking>) {
        val rows = JSONArray()
        bookings.forEach { b ->
            rows.put(JSONObject().put("id", b.id).put("requesterId", b.requesterId).put("patient", b.patient)
                .put("hospital", b.hospital).put("department", b.department).put("date", b.date).put("time", b.time)
                .put("meeting", b.meeting).put("support", b.support).put("note", b.note).put("manager", b.manager).put("status", b.status).put("patientId", b.patientId))
        }
        check(preferences.edit().putString("bookings", rows.toString()).commit()) { "예약을 저장하지 못했습니다." }
    }
}
