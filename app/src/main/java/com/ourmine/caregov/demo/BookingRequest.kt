package com.ourmine.caregov.demo

enum class VisitService(val label: String) {
    OUTPATIENT("외래 진료"), EXAMINATION("검사·검진"), RETURN_HOME("진료 후 귀가"),
}
enum class SharingScope(val label: String) {
    NONE("공유하지 않음"), PROGRESS("진행 상황만"), RESULTS("동행 결과 포함"),
}
data class RequestOptions(
    val patientPhone: String = "",
    val relationship: String = "본인",
    val guardianPhone: String = "",
    val sharing: SharingScope = SharingScope.PROGRESS,
    val service: VisitService = VisitService.OUTPATIENT,
    val hours: Int = 2,
    val patientAccountId: String = "",
    val consent: Boolean = false,
    val estimatedWon: Long? = null,
)
data class BookingDraft(
    val patient: String, val hospital: String, val department: String,
    val date: String, val time: String, val meeting: String,
    val support: String, val note: String, val options: RequestOptions,
)

object ServicePricing {
    // Fictional quote requested for the mockup; not an approved commercial tariff.
    val hourlyWon: Long? = 20_000
    const val minimumHours = 1
    fun estimate(hours: Int, hourlyRate: Long? = hourlyWon, minimum: Int = minimumHours): Long? {
        require(hours in 1..8 && minimum in 1..8)
        if (hourlyRate == null) return null
        require(hourlyRate in 1..1_000_000)
        return hourlyRate * maxOf(hours, minimum)
    }
}

fun normalizedPhone(value: String) = value.filter(Char::isDigit)
fun validPhone(value: String) = normalizedPhone(value).matches(Regex("01[016789][0-9]{7,8}"))
