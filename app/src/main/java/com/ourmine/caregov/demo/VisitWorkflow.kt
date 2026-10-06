package com.ourmine.caregov.demo

import org.json.JSONArray
import org.json.JSONObject

data class VisitEvent(val title: String, val at: String, val actor: String)
data class VisitReport(
    val summary: String, val instructions: String = "", val medication: String = "",
    val nextVisit: String = "", val minutes: Int = 120, val submittedAt: String = "",
    val feeWon: Long = 0, val managerWon: Long = 0,
)
data class VisitWorkflow(
    val events: List<VisitEvent> = emptyList(), val report: VisitReport? = null,
    val settlementAt: String = "", val rating: Int = 0, val review: String = "",
) {
    fun toJson(): JSONObject {
        val rows = JSONArray()
        events.forEach { rows.put(JSONObject().put("title", it.title).put("at", it.at).put("actor", it.actor)) }
        val value = JSONObject().put("events", rows).put("settlementAt", settlementAt).put("rating", rating).put("review", review)
        report?.let { value.put("report", JSONObject().put("summary", it.summary).put("instructions", it.instructions)
            .put("medication", it.medication).put("nextVisit", it.nextVisit).put("minutes", it.minutes)
            .put("submittedAt", it.submittedAt).put("feeWon", it.feeWon).put("managerWon", it.managerWon)) }
        return value
    }
    companion object {
        fun fromJson(value: JSONObject?): VisitWorkflow {
            if (value == null) return VisitWorkflow()
            val rows = value.optJSONArray("events") ?: JSONArray()
            val report = value.optJSONObject("report")?.let { VisitReport(it.getString("summary"), it.optString("instructions"),
                it.optString("medication"), it.optString("nextVisit"), it.getInt("minutes"), it.optString("submittedAt"),
                it.optLong("feeWon"), it.optLong("managerWon")) }
            return VisitWorkflow((0 until rows.length()).map { rows.getJSONObject(it).let { row ->
                VisitEvent(row.getString("title"), row.getString("at"), row.getString("actor"))
            } }, report, value.optString("settlementAt"), value.optInt("rating"), value.optString("review"))
        }
    }
}

object VisitSteps {
    val beforeVisit = setOf("접수 완료", "배정 대기", "예약 확정")
    fun progress(service: VisitService): List<String> = when (service) {
        VisitService.OUTPATIENT -> listOf("예약 확정", "만남 완료", "병원 도착", "진료 완료", "귀가 완료")
        VisitService.EXAMINATION -> listOf("예약 확정", "만남 완료", "병원 도착", "검사 완료", "귀가 완료")
        VisitService.RETURN_HOME -> listOf("예약 확정", "만남 완료", "귀가 완료")
    }
    fun next(booking: Booking): String? {
        val steps = progress(booking.options.service)
        val index = steps.indexOf(booking.status)
        return if (index >= 0) steps.getOrNull(index + 1) else null
    }
}

object SettlementPricing {
    // Fictional service/settlement rules; no charge or transfer is initiated.
    fun fee(minutes: Int): Long {
        require(minutes in 1..480) { "이용 시간은 1분부터 480분까지 입력해 주세요." }
        val halfHours = maxOf(2, (minutes + 29) / 30)
        return halfHours * 10_000L
    }
    fun manager(fee: Long) = fee * 80 / 100
}
