package com.tenda.wifimonitor.data.network

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

data class RouterDevice(
    val macAddress: String,
    val ipAddress: String,
    val hostname: String,
    val connectionType: String = "",   // e.g. "wifi_2G", "wired"
    val connectedSeconds: Long = 0,
)

data class RouterStatus(
    val wanIp: String,
    val uptime: String,
    val connectedCount: Int,
)

@Singleton
class TendaApiService @Inject constructor() {

    private val TAG = "TendaApiService"
    private val BASE_URL = "http://192.168.0.1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private var sessionCookie: String? = null
    private var successfulLoginStrategy: Pair<String, String>? = null

    /**
     * Returns true if the response body looks like a valid JSON API response.
     * Tenda routers redirect to a login page (HTML) when the session is expired.
     */
    private fun isAuthError(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("<!")
            || trimmed.startsWith("<html")
            || body.contains("window.location", ignoreCase = true)
            || body.contains("/login.html", ignoreCase = true)
            || body.isBlank()
    }

    // ── Authentication ──────────────────────────────────────────────────────────

    suspend fun login(password: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val encodedPassword = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
        
        // Use cached strategy if we have successfully logged in before
        val loginAttempts = if (successfulLoginStrategy != null) {
            listOf(successfulLoginStrategy!!)
        } else {
            listOf(
                // Modern F3: Base64 to /login/Auth
                Pair("$BASE_URL/login/Auth", encodedPassword),
                // Older F3: Plaintext to /login/Auth
                Pair("$BASE_URL/login/Auth", password),
                // Some versions: Base64 to /goform/SysToolLogin
                Pair("$BASE_URL/goform/SysToolLogin", encodedPassword),
                // Some versions: Plaintext to /goform/SysToolLogin
                Pair("$BASE_URL/goform/SysToolLogin", password)
            )
        }

        for (strategy in loginAttempts) {
            val (url, pwd) = strategy
            try {
                val body = FormBody.Builder()
                    .add("password", pwd)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val cookie = response.header("Set-Cookie")
                
                if (cookie != null) {
                    sessionCookie = cookie.split(";").firstOrNull()
                    successfulLoginStrategy = strategy
                    Log.d(TAG, "Login OK via $url, cookie=$sessionCookie")
                    return@withContext true
                } else if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.d(TAG, "Login body via $url: $bodyStr")
                    // If it returns JSON containing "errCode":0, it's successful even without a cookie
                    if (bodyStr.contains("\"errCode\":0") || bodyStr.contains("\"errCode\":\"0\"") || bodyStr.contains("success")) {
                        successfulLoginStrategy = strategy
                        Log.d(TAG, "Login OK via $url (JSON response)")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                // If it's a NetworkOnMainThreadException, this will print "null"
                Log.e(TAG, "Login attempt failed for $url: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
        
        // If cached strategy failed, reset it so next time we try all
        successfulLoginStrategy = null
        Log.e(TAG, "All login attempts failed.")
        return@withContext false
    }

    // ── Connected Devices ────────────────────────────────────────────────────────

    suspend fun getConnectedDevices(): List<RouterDevice> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            // The Tenda F3 uses a GET request with query params to /goform/getQos
            val url = "$BASE_URL/goform/getQos?modules=localhost,onlineList,macFilter,guestList,wifiBasicCfg"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (isAuthError(body)) {
                // Session expired — signal via empty list so the caller can re-login
                Log.w(TAG, "getConnectedDevices: auth error detected (redirect/HTML), session expired")
                emptyList()
            } else {
                Log.d(TAG, "getConnectedDevices response: $body")
                parseQosDeviceList(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConnectedDevices error: ${e.javaClass.simpleName} - ${e.message}")
            emptyList()
        }
    }

    private fun parseQosDeviceList(json: String): List<RouterDevice> {
        val devices = mutableListOf<RouterDevice>()
        try {
            val root = JSONObject(json)
            val onlineList = root.optJSONArray("onlineList") ?: return devices
            for (i in 0 until onlineList.length()) {
                val d = onlineList.optJSONObject(i) ?: continue
                val mac = d.optString("qosListMac")
                if (mac.isBlank()) continue
                val ip = d.optString("qosListIP")
                // Use remark (user-set name) if present, otherwise hostname
                val remark = d.optString("qosListRemark")
                val hostname = d.optString("qosListHostname")
                val displayName = if (remark.isNotBlank()) remark else hostname.ifBlank { "Unknown" }
                val connectType = d.optString("qosListAccessType") // e.g. "wifi_2G", "wired"
                val connectSeconds = d.optLong("qoslistConnetTime", 0)

                devices.add(RouterDevice(
                    macAddress = mac.uppercase(),
                    ipAddress = ip,
                    hostname = displayName,
                    connectionType = connectType,
                    connectedSeconds = connectSeconds,
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseQosDeviceList error: ${e.message}")
        }
        return devices
    }

    // ── Router Status ────────────────────────────────────────────────────────────

    suspend fun getRouterStatus(): RouterStatus? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val url = "$BASE_URL/goform/getQos?modules=localhost,onlineList,macFilter,wifiBasicCfg"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val connectedCount = root.optJSONArray("onlineList")?.length() ?: 0
            RouterStatus(
                wanIp = "Connected",
                uptime = "N/A",
                connectedCount = connectedCount,
            )
        } catch (e: Exception) {
            Log.e(TAG, "getRouterStatus: ${e.message}")
            null
        }
    }

    // ── Block / Unblock Device ────────────────────────────────────────────────────

    suspend fun blockDevice(mac: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val json = postGoformWithParams(
                "setMacFilterList",
                mapOf("mac" to mac, "type" to "1") // 1 = block
            )
            json != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unblockDevice(mac: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val json = postGoformWithParams(
                "setMacFilterList",
                mapOf("mac" to mac, "type" to "0") // 0 = allow
            )
            json != null
        } catch (e: Exception) {
            false
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    private fun postGoform(action: String): String? {
        val body = FormBody.Builder()
            .add("action", action)
            .build()
        return executePost("$BASE_URL/goform/$action", body)
    }

    private fun postGoformWithParams(action: String, params: Map<String, String>): String? {
        val bodyBuilder = FormBody.Builder().add("action", action)
        params.forEach { (k, v) -> bodyBuilder.add(k, v) }
        return executePost("$BASE_URL/goform/$action", bodyBuilder.build())
    }

    private fun executePost(url: String, body: okhttp3.RequestBody): String? {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
        sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
        val response = client.newCall(requestBuilder.build()).execute()
        return if (response.isSuccessful) response.body?.string() else null
    }

    private fun formatUptime(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return "${d}d ${h}h ${m}m"
    }

    fun clearSession() {
        sessionCookie = null
    }
}
