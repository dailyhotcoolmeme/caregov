package expo.modules.caregovlegacy

import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CaregovLegacyModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("CaregovLegacyStore")
    Function("read") {
      val context = requireNotNull(appContext.reactContext)
      val prefs = context.getSharedPreferences("service_records", Context.MODE_PRIVATE)
      mapOf(
        "bookings" to prefs.getString("bookings", null),
        "account" to prefs.getString("account", "patient"),
        "signedOut" to prefs.getBoolean("signed_out", false)
      )
    }
  }
}
