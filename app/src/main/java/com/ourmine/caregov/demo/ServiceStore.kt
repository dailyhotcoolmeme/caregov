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
    val options: RequestOptions = RequestOptions(),
    val revision: Int = 0,
    val cancellationReason: String = "",
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
            val options = r.optJSONObject("options") ?: JSONObject()
            Booking(r.getString("id"), r.getString("requesterId"), r.getString("patient"),
                r.getString("hospital"), r.getString("department"), r.getString("date"), r.getString("time"),
                r.getString("meeting"), r.getString("support"), r.getString("note"), r.optString("manager"), r.getString("status"), r.optString("patientId"),
                RequestOptions(options.optString("patientPhone"), options.optString("relationship", "본인"), options.optString("guardianPhone"),
                    SharingScope.entries.firstOrNull { it.name == options.optString("sharing") } ?: SharingScope.PROGRESS,
                    VisitService.entries.firstOrNull { it.name == options.optString("service") } ?: VisitService.OUTPATIENT,
                    options.optInt("hours", 2), r.optString("patientId"), options.optBoolean("consent"),
                    if (options.has("estimatedWon") && !options.isNull("estimatedWon")) options.getLong("estimatedWon") else null),
                r.optInt("revision"), r.optString("cancellationReason"))
        }
    }
    fun visible(account: ServiceAccount, bookings: List<Booking>): List<Booking> = bookings.filter {
        when (account.role) {
            DemoRole.PATIENT -> it.requesterId == account.id || it.patientId == account.id
            DemoRole.GUARDIAN -> it.requesterId == account.id ||
                (account.id == "guardian" && it.patientId == "patient" && it.options.sharing != SharingScope.NONE)
            DemoRole.MANAGER -> it.manager == account.name
            DemoRole.OPERATOR -> true
        }
    }
    fun canReadReport(account: ServiceAccount, booking: Booking): Boolean = when (account.role) {
        DemoRole.PATIENT -> booking.patientId == account.id || booking.requesterId == account.id
        DemoRole.GUARDIAN -> visible(account, listOf(booking)).isNotEmpty() && booking.options.sharing == SharingScope.RESULTS
        DemoRole.MANAGER -> booking.manager == account.name
        DemoRole.OPERATOR -> true
    }
    fun canModify(account: ServiceAccount, booking: Booking): Boolean =
        account.id == booking.requesterId && (account.role == DemoRole.PATIENT || account.role == DemoRole.GUARDIAN) &&
            booking.status == "접수 완료" && LocalDateTime.of(LocalDate.parse(booking.date), LocalTime.parse(booking.time)).isAfter(LocalDateTime.now())

    fun create(account: ServiceAccount, draft: BookingDraft): Booking {
        val validated = validate(account, draft)
        val booking = Booking("CG-${UUID.randomUUID().toString().take(8).uppercase()}", account.id,
            validated.patient, validated.hospital, validated.department, validated.date, validated.time,
            validated.meeting, validated.support, validated.note, patientId = validated.options.patientAccountId, options = validated.options)
        save(bookings() + booking)
        return booking
    }
    fun update(account: ServiceAccount, id: String, revision: Int, draft: BookingDraft): Booking {
        val records = bookings()
        val original = records.first { it.id == id }
        require(canModify(account, original)) { "변경할 수 없는 예약입니다." }
        require(original.revision == revision) { "예약이 변경되었습니다. 다시 확인해 주세요." }
        val value = validate(account, draft)
        val updated = original.copy(patient = value.patient, hospital = value.hospital, department = value.department,
            date = value.date, time = value.time, meeting = value.meeting, support = value.support, note = value.note,
            patientId = value.options.patientAccountId, options = value.options, revision = revision + 1)
        save(records.map { if (it.id == id) updated else it })
        return updated
    }
    fun cancel(account: ServiceAccount, id: String, revision: Int, reason: String): Booking {
        val records = bookings()
        val original = records.first { it.id == id }
        require(canModify(account, original)) { "취소할 수 없는 예약입니다." }
        require(original.revision == revision) { "예약이 변경되었습니다. 다시 확인해 주세요." }
        require(reason.isNotBlank() && reason.length <= 200) { "취소 사유를 확인해 주세요." }
        val cancelled = original.copy(status = "신청 취소", cancellationReason = reason.trim(), revision = revision + 1)
        save(records.map { if (it.id == id) cancelled else it })
        return cancelled
    }
    private fun validate(account: ServiceAccount, draft: BookingDraft): BookingDraft {
        require(accounts.contains(account)) { "계정 정보를 확인해 주세요." }
        require(account.role == DemoRole.PATIENT || account.role == DemoRole.GUARDIAN)
        require(listOf(draft.patient, draft.hospital, draft.department, draft.meeting).all { it.isNotBlank() && it.length <= 120 }) { "이용자와 병원, 만날 장소를 확인해 주세요." }
        require(draft.note.length <= 1000)
        require(LocalDateTime.of(LocalDate.parse(draft.date), LocalTime.parse(draft.time)).isAfter(LocalDateTime.now())) { "방문 일시는 현재 이후로 선택해 주세요." }
        require(draft.support in listOf("도보 이동", "보행 보조", "휠체어 이용"))
        val options = draft.options
        require(options.consent) { "신청 동의 여부를 확인해 주세요." }
        require(validPhone(options.patientPhone)) { "이용자 연락처를 확인해 주세요." }
        require(options.guardianPhone.isBlank() || validPhone(options.guardianPhone)) { "보호자 연락처를 확인해 주세요." }
        require(options.hours in ServicePricing.minimumHours..8)
        if (account.role == DemoRole.GUARDIAN) {
            require(options.relationship in listOf("어머니", "아버지", "배우자", "자녀", "친척", "기타")) { "이용자와의 관계를 선택해 주세요." }
            require(validPhone(options.guardianPhone)) { "보호자 연락처를 확인해 주세요." }
            if (options.patientAccountId.isNotBlank()) require(account.id == "guardian" && options.patientAccountId == "patient" && draft.patient == "김영희")
        } else require(draft.patient == account.name) { "본인 신청은 본인 이름으로 접수해야 합니다." }
        val patientId = if (account.role == DemoRole.PATIENT) account.id else options.patientAccountId
        return draft.copy(patient = draft.patient.trim(), hospital = draft.hospital.trim(), department = draft.department.trim(),
            meeting = draft.meeting.trim(), note = draft.note.trim(), options = options.copy(patientPhone = normalizedPhone(options.patientPhone),
                guardianPhone = normalizedPhone(options.guardianPhone), relationship = if (account.role == DemoRole.PATIENT) "본인" else options.relationship,
                patientAccountId = patientId, estimatedWon = ServicePricing.estimate(options.hours)))
    }
    private fun save(bookings: List<Booking>) {
        val rows = JSONArray()
        bookings.forEach { b ->
            rows.put(JSONObject().put("id", b.id).put("requesterId", b.requesterId).put("patient", b.patient)
                .put("hospital", b.hospital).put("department", b.department).put("date", b.date).put("time", b.time)
                .put("meeting", b.meeting).put("support", b.support).put("note", b.note).put("manager", b.manager).put("status", b.status).put("patientId", b.patientId)
                .put("revision", b.revision).put("cancellationReason", b.cancellationReason)
                .put("options", JSONObject().put("patientPhone", b.options.patientPhone).put("relationship", b.options.relationship)
                    .put("guardianPhone", b.options.guardianPhone).put("sharing", b.options.sharing.name).put("service", b.options.service.name)
                    .put("hours", b.options.hours).put("consent", b.options.consent).put("estimatedWon", b.options.estimatedWon ?: JSONObject.NULL)))
        }
        check(preferences.edit().putString("bookings", rows.toString()).commit()) { "예약을 저장하지 못했습니다." }
    }
}
