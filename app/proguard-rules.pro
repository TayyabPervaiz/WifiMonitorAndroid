# -----------------------------------------------------------------------------
# Tenda WiFi Monitor - ProGuard / R8 keep rules.
#
# Only rules not already provided by library consumer-rules are listed here.
# -----------------------------------------------------------------------------

# --- Kotlin metadata (needed for reflection) ---
-keepattributes Signature
-keepattributes *Annotation*

# --- Hilt ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# --- Room generated code ---
-keep class com.tenda.wifimonitor.data.db.** { *; }

# --- Glance widget (loaded by class name) ---
-keep class com.tenda.wifimonitor.widget.** { *; }

# --- Tink (pulled in transitively by androidx.security:security-crypto) ---
# Tink is annotated with Error Prone annotations that are compile-only and
# absent at runtime, so R8 only needs to be told not to warn about them.
-dontwarn com.google.errorprone.annotations.**
