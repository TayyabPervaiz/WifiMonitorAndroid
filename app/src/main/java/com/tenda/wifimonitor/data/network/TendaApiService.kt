package com.tenda.wifimonitor.data.network

import android.util.Log
import com.tenda.wifimonitor.BuildConfig
import com.tenda.wifimonitor.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

// ── Data Classes ──────────────────────────────────────────────────────────────

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

/**
 * Full router status matching the Tenda web portal Status page.
 */
data class RouterFullStatus(
    val connectionType: String,         // e.g. "PPPoE", "Dynamic IP Address"
    val wanIp: String,                  // WAN IP address
    val uptime: String,                 // human-readable uptime string
    val uptimeSeconds: Long,            // raw seconds for display formatting
    val subnetMask: String,             // e.g. "255.255.255.254"
    val routerMac: String,              // router's own WAN MAC
    val defaultGateway: String,         // upstream gateway IP
    val lanIp: String = "192.168.0.1",   // router LAN IP
    val preferredDns: String = "—",     // primary DNS server
    val alternateDns: String = "—",     // secondary DNS server
    val firmwareVersion: String = "—",  // router firmware version
    val isConnected: Boolean,
    val downloadSpeedKbps: Float,       // total WAN download speed in Kbps
    val uploadSpeedKbps: Float,         // total WAN upload speed in Kbps
    val onlineDeviceCount: Int,
)

/**
 * Per-device bandwidth info for the Bandwidth Control screen.
 *
 * LIMIT UNITS — The Tenda F3 stores limits in a proprietary raw unit:
 *   - `rawValue / 128 = Mbps`   (e.g. 256 = 2.00 Mbps)
 *   - `rawValue * 128 = limit`   (e.g. 2.00 Mbps → raw 256)
 *   - `38528` = sentinel for "No limit"
 *   - `0` = internet access blocked (all traffic stopped)
 *
 * SPEED UNITS — The router returns raw speed values:
 *   - If value >= 128: `value / 128` = Mbps
 *   - If value < 128:  `value * 8` = Kbps
 */
data class DeviceBandwidthInfo(
    val macAddress: String,
    val hostname: String,            // display name (remark or hostname)
    val rawHostname: String = "",    // original hostname (needed for POST)
    val remark: String = "",         // user-set remark (needed for POST)
    val ipAddress: String = "",
    val connectionType: String = "",
    val downloadSpeedRaw: Float = 0f,     // raw speed value from router
    val uploadSpeedRaw: Float = 0f,       // raw speed value from router
    val downloadLimitRaw: Float = NO_LIMIT_RAW, // raw limit (/ 128 = Mbps, 38528 = no limit)
    val uploadLimitRaw: Float = NO_LIMIT_RAW,   // raw limit (/ 128 = Mbps, 38528 = no limit)
    val internetAccessEnabled: Boolean = true, // false = "Local" in web UI (qosListAccess = "false")
    val isLocal: Boolean = false,    // true if this is the device accessing the router/app
) {
    companion object {
        const val NO_LIMIT_RAW = 38528f

        /** Convert Mbps display value to raw router unit. */
        fun mbpsToRaw(mbps: Float): Float =
            if (mbps <= 0f) NO_LIMIT_RAW else (mbps * 128f)

        /** Convert raw router unit to Mbps. Returns 0 for "no limit". */
        fun rawToMbps(raw: Float): Float =
            if (raw >= 38401f) 0f else raw / 128f

        /** Format raw speed to display string (matching web UI logic). */
        fun formatSpeed(raw: Float): String =
            if (raw >= 128f) "%.2f Mbps".format(raw / 128f)
            else "%.0f Kbps".format(raw * 8f)

        /** Format raw limit to display string. */
        fun formatLimit(raw: Float): String =
            if (raw >= 38401f) "No limit"
            else "%.2f Mbps".format(raw / 128f)
    }
}


// ── Service ───────────────────────────────────────────────────────────────────

@Singleton
class TendaApiService @Inject constructor(
    private val preferences: AppPreferences,
) {
    private val TAG = "TendaApiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private var sessionCookie: String? = null
    private var successfulLoginStrategy: Pair<String, String>? = null

    @Volatile var localDeviceMac: String? = null
    @Volatile var localDeviceIp: String? = null

    /**
     * Checks whether a MAC or IP corresponds to this device running the app.
     * Uses router's reported localhost info as well as local network interface IP.
     */
    fun isLocalDevice(mac: String, ip: String): Boolean {
        val phoneIp = localDeviceIp ?: NetworkUtils.getLocalIpAddress()
        if (!localDeviceMac.isNullOrBlank() && mac.isNotBlank() && mac.equals(localDeviceMac, ignoreCase = true)) {
            return true
        }
        if (!phoneIp.isNullOrBlank() && ip.isNotBlank() && ip == phoneIp) {
            return true
        }
        return false
    }

    // ── Dynamic base URL (reads from DataStore each time) ─────────────────────

    private suspend fun baseUrl(): String =
        (preferences.routerUrl.firstOrNull() ?: "http://192.168.0.1").trimEnd('/')

    /**
     * Returns true if the response body looks like an HTML auth redirect.
     * Tenda routers redirect to a login page when the session expires.
     */
    private fun isAuthError(body: String): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("<!")
            || trimmed.startsWith("<html")
            || body.contains("window.location", ignoreCase = true)
            || body.contains("/login.html", ignoreCase = true)
            || body.isBlank()
    }

    // ── Authentication ────────────────────────────────────────────────────────

    suspend fun login(password: String): Boolean = withContext(Dispatchers.IO) {
        val base = baseUrl()
        val encodedPassword = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)

        val loginAttempts = if (successfulLoginStrategy != null) {
            listOf(successfulLoginStrategy!!)
        } else {
            listOf(
                Pair("$base/login/Auth", encodedPassword),
                Pair("$base/login/Auth", password),
                Pair("$base/goform/SysToolLogin", encodedPassword),
                Pair("$base/goform/SysToolLogin", password),
            )
        }

        for (strategy in loginAttempts) {
            val (url, pwd) = strategy
            try {
                val body = FormBody.Builder().add("password", pwd).build()
                val request = Request.Builder().url(url).post(body).build()
                val response = client.newCall(request).execute()
                val cookie = response.header("Set-Cookie")

                if (cookie != null) {
                    sessionCookie = cookie.split(";").firstOrNull()
                    successfulLoginStrategy = strategy
                    Log.d(TAG, "Login OK via $url, cookie=$sessionCookie")
                    return@withContext true
                } else if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.contains("\"errCode\":0") ||
                        bodyStr.contains("\"errCode\":\"0\"") ||
                        bodyStr.contains("success")
                    ) {
                        successfulLoginStrategy = strategy
                        if (BuildConfig.DEBUG) Log.d(TAG, "Login OK via $url (JSON response)")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login attempt failed for $url: ${e.javaClass.simpleName} - ${e.message}")
            }
        }

        successfulLoginStrategy = null
        Log.e(TAG, "All login attempts failed.")
        return@withContext false
    }

    // ── Connected Devices ─────────────────────────────────────────────────────

    suspend fun getConnectedDevices(): List<RouterDevice> = withContext(Dispatchers.IO) {
        val base = baseUrl()
        return@withContext try {
            val url = "$base/goform/getQos?modules=localhost,onlineList,macFilter,guestList,wifiBasicCfg"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (isAuthError(body)) {
                Log.w(TAG, "getConnectedDevices: auth error — session expired")
                emptyList()
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "getConnectedDevices response: $body")
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
            val localhostObj = root.optJSONObject("localhost")
            if (localhostObj != null) {
                val mac = localhostObj.optString("localhostMAC")
                val ip = localhostObj.optString("localhost")
                if (mac.isNotBlank()) localDeviceMac = mac.uppercase()
                if (ip.isNotBlank()) localDeviceIp = ip
            }
            val onlineList = root.optJSONArray("onlineList") ?: return devices
            for (i in 0 until onlineList.length()) {
                val d = onlineList.optJSONObject(i) ?: continue
                val mac = d.optString("qosListMac")
                if (mac.isBlank()) continue
                val ip = d.optString("qosListIP")
                val remark = d.optString("qosListRemark")
                val hostname = d.optString("qosListHostname")
                val displayName = if (remark.isNotBlank()) remark else hostname.ifBlank { "Unknown" }
                val connectType = d.optString("qosListAccessType")
                val connectSeconds = d.optLong("qoslistConnetTime", 0)

                devices.add(
                    RouterDevice(
                        macAddress = mac.uppercase(),
                        ipAddress = ip,
                        hostname = displayName,
                        connectionType = connectType,
                        connectedSeconds = connectSeconds,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseQosDeviceList error: ${e.message}")
        }
        return devices
    }

    // ── Router Status (basic, for backwards compat) ───────────────────────────

    suspend fun getRouterStatus(): RouterStatus? = withContext(Dispatchers.IO) {
        val base = baseUrl()
        return@withContext try {
            val url = "$base/goform/getQos?modules=localhost,onlineList,macFilter,wifiBasicCfg"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val connectedCount = root.optJSONArray("onlineList")?.length() ?: 0
            RouterStatus(wanIp = "Connected", uptime = "N/A", connectedCount = connectedCount)
        } catch (e: Exception) {
            Log.e(TAG, "getRouterStatus: ${e.message}")
            null
        }
    }

    // ── Full Router Status (Status Screen) ────────────────────────────────────

    /**
     * Fetches comprehensive router status for the Status screen.
     * Tries to extract WAN info, system info, and real-time speed from the
     * getQos endpoint. Falls back gracefully if fields are missing.
     */
    suspend fun getFullRouterStatus(): RouterFullStatus? = withContext(Dispatchers.IO) {
        val base = baseUrl()
        return@withContext try {
            val url = "$base/goform/getStatus?modules=internetStatus,deviceStatistics,systemInfo,wanAdvCfg,wifiRelay,wifiBasicCfg,sysTime"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: return@withContext null

            if (isAuthError(body)) {
                Log.w(TAG, "getFullRouterStatus: auth error")
                return@withContext null
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "getFullRouterStatus response: $body")
            var status = parseFullRouterStatus(body)

            // If speeds or device count were 0 or missing from getStatus, supplement with getDeviceBandwidthList()
            if (status != null && (status.onlineDeviceCount == 0 || (status.downloadSpeedKbps == 0f && status.uploadSpeedKbps == 0f))) {
                try {
                    val devices = getDeviceBandwidthList()
                    if (devices.isNotEmpty()) {
                        val count = devices.count { it.internetAccessEnabled }
                        val dlTotal = devices.sumOf { (it.downloadSpeedRaw * 8.0) }.toFloat()
                        val ulTotal = devices.sumOf { (it.uploadSpeedRaw * 8.0) }.toFloat()
                        status = status.copy(
                            onlineDeviceCount = if (status.onlineDeviceCount == 0) count else status.onlineDeviceCount,
                            downloadSpeedKbps = if (status.downloadSpeedKbps == 0f) dlTotal else status.downloadSpeedKbps,
                            uploadSpeedKbps = if (status.uploadSpeedKbps == 0f) ulTotal else status.uploadSpeedKbps,
                        )
                    }
                } catch (_: Exception) {}
            }
            status
        } catch (e: Exception) {
            Log.e(TAG, "getFullRouterStatus error: ${e.message}")
            null
        }
    }

    private fun parseFullRouterStatus(json: String): RouterFullStatus? {
        return try {
            val root = JSONObject(json)
            val sysInfo = root.optJSONObject("systemInfo") ?: root
            val internetStatus = root.optJSONObject("internetStatus")
            val devStats = root.optJSONObject("deviceStatistics")
                ?: root.optJSONObject("deviceStastics")

            // WAN connection type mapping
            val rawWanType = sysInfo.optString("wanType")
                .ifBlank { sysInfo.optString("wanConnectType")
                .ifBlank { sysInfo.optString("connectType", "") } }
            val connType = when (rawWanType.lowercase()) {
                "pppoe" -> "PPPoE"
                "dhcp" -> "Dynamic IP Address"
                "static" -> "Static IP Address"
                "pppoe2" -> "PPPoE Russia"
                "pptp" -> "PPTP/PPTP Russia"
                "l2tp" -> "L2TP/L2TP Russia"
                else -> if (rawWanType.isNotBlank()) rawWanType else "PPPoE"
            }

            // System / WAN Info - Exact keys from Tenda F3 firmware
            val wanIp = sysInfo.optString("statusWanIP")
                .ifBlank { sysInfo.optString("wanIp")
                .ifBlank { sysInfo.optString("wanIP", "—") } }

            val uptimeSecs = sysInfo.optLong("wanConnectTime", 0L)
                .let { if (it == 0L) sysInfo.optString("wanConnectTime").toLongOrNull() ?: 0L else it }
                .let { if (it == 0L) sysInfo.optLong("wanUpTime", 0L) else it }
                .let { if (it == 0L) sysInfo.optLong("uptime", 0L) else it }

            val subnetMask = sysInfo.optString("statusWanMask")
                .ifBlank { sysInfo.optString("mask")
                .ifBlank { sysInfo.optString("subNetMask", "—") } }

            // Note: Tenda firmware has a typo in the key name: "statusWanGaterway"
            val gateway = sysInfo.optString("statusWanGaterway")
                .ifBlank { sysInfo.optString("statusWanGateway")
                .ifBlank { sysInfo.optString("gateway")
                .ifBlank { sysInfo.optString("defaultGateway", "—") } } }

            val routerMac = sysInfo.optString("statusWanMAC")
                .ifBlank { sysInfo.optString("mac")
                .ifBlank { sysInfo.optString("wanMac")
                .ifBlank { sysInfo.optString("macAddr", "—") } } }

            val lanIp = sysInfo.optString("lanIP")
                .ifBlank { sysInfo.optString("lanIp", "192.168.0.1") }

            val preferredDns = sysInfo.optString("statusWanDns1")
                .ifBlank { sysInfo.optString("dns1")
                .ifBlank { sysInfo.optString("dns", "—") } }

            val alternateDns = sysInfo.optString("statusWanDns2")
                .ifBlank { sysInfo.optString("dns2", "—") }.ifBlank { "—" }

            val firmwareVersion = sysInfo.optString("softVersion")
                .ifBlank { sysInfo.optString("version")
                .ifBlank { sysInfo.optString("firmwareVersion", "—") } }

            // Connection Status from internetStatus.wanConnectStatus
            val wanConnectStatus = internetStatus?.optString("wanConnectStatus", "") ?: ""
            val codeSlice = if (wanConnectStatus.length >= 7) wanConnectStatus.substring(3, 7) else ""
            val isConnectedCode = codeSlice in setOf("0106", "0206", "0306", "0506", "0606", "0806", "1506", "1606", "1806") ||
                    codeSlice.startsWith("11") || codeSlice.startsWith("12") || codeSlice.startsWith("13") ||
                    codeSlice.startsWith("21") || codeSlice.startsWith("22") || codeSlice.startsWith("23") ||
                    codeSlice.startsWith("41") || codeSlice.startsWith("42") || codeSlice.startsWith("43")
            val hasValidIp = wanIp.isNotBlank() && wanIp != "—" && wanIp != "0.0.0.0"
            val isConnected = isConnectedCode || hasValidIp

            // Speeds and device count from deviceStastics / deviceStatistics
            var onlineCount = devStats?.optString("statusOnlineNumber")?.toIntOrNull()
                ?: devStats?.optInt("statusOnlineNumber", 0) ?: 0
            val dlSpeedRaw = devStats?.optString("statusDownSpeed", "0.00")?.toFloatOrNull() ?: 0f
            val ulSpeedRaw = devStats?.optString("statusUpSpeed", "0.00")?.toFloatOrNull() ?: 0f
            var totalDlKbps = dlSpeedRaw * 8f
            var totalUlKbps = ulSpeedRaw * 8f

            // Fallback: If onlineList is also in response, extract count & speeds
            val onlineList = root.optJSONArray("onlineList")
            if (onlineList != null && onlineList.length() > 0) {
                if (onlineCount == 0) onlineCount = onlineList.length()
                if (totalDlKbps == 0f && totalUlKbps == 0f) {
                    for (i in 0 until onlineList.length()) {
                        val d = onlineList.optJSONObject(i) ?: continue
                        val dl = d.optString("qosListDownSpeed", "0.00").toFloatOrNull() ?: 0f
                        val ul = d.optString("qosListUpSpeed", "0.00").toFloatOrNull() ?: 0f
                        totalDlKbps += dl * 8f
                        totalUlKbps += ul * 8f
                    }
                }
            }

            RouterFullStatus(
                connectionType = connType,
                wanIp = wanIp,
                uptime = formatUptime(uptimeSecs),
                uptimeSeconds = uptimeSecs,
                subnetMask = subnetMask,
                routerMac = routerMac.uppercase(),
                defaultGateway = gateway,
                lanIp = lanIp,
                preferredDns = preferredDns,
                alternateDns = alternateDns,
                firmwareVersion = firmwareVersion,
                isConnected = isConnected,
                downloadSpeedKbps = totalDlKbps,
                uploadSpeedKbps = totalUlKbps,
                onlineDeviceCount = onlineCount,
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseFullRouterStatus error: ${e.message}")
            null
        }
    }

    // ── Bandwidth Control ─────────────────────────────────────────────────────

    /**
     * Returns per-device bandwidth info including live speeds and QoS limits.
     */
    suspend fun getDeviceBandwidthList(): List<DeviceBandwidthInfo> = withContext(Dispatchers.IO) {
        val base = baseUrl()
        return@withContext try {
            val url = "$base/goform/getQos?modules=localhost,onlineList,macFilter,wifiBasicCfg"
            val requestBuilder = Request.Builder().url(url).get()
            sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
            val response = client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (isAuthError(body)) {
                Log.w(TAG, "getDeviceBandwidthList: auth error")
                return@withContext emptyList()
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "getDeviceBandwidthList response: $body")
            parseDeviceBandwidthList(body)
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceBandwidthList error: ${e.message}")
            emptyList()
        }
    }

    private fun parseDeviceBandwidthList(json: String): List<DeviceBandwidthInfo> {
        val result = mutableListOf<DeviceBandwidthInfo>()
        try {
            val root = JSONObject(json)
            val localhostObj = root.optJSONObject("localhost")
            if (localhostObj != null) {
                val mac = localhostObj.optString("localhostMAC")
                val ip = localhostObj.optString("localhost")
                if (mac.isNotBlank()) localDeviceMac = mac.uppercase()
                if (ip.isNotBlank()) localDeviceIp = ip
            }
            val onlineList = root.optJSONArray("onlineList") ?: JSONArray()

            // macFilter contains devices blocked from internet access
            val macFilter = root.optJSONObject("macFilter")
            val macFilterList = macFilter?.optJSONArray("macFilterList")
            val blockedMacs = mutableSetOf<String>()
            if (macFilterList != null) {
                for (i in 0 until macFilterList.length()) {
                    val entry = macFilterList.optJSONObject(i) ?: continue
                    if (entry.optString("filterMode") != "pass") {
                        val mac = entry.optString("mac").uppercase()
                        if (mac.isNotBlank()) blockedMacs.add(mac)
                    }
                }
            }

            for (i in 0 until onlineList.length()) {
                val d = onlineList.optJSONObject(i) ?: continue
                val mac = d.optString("qosListMac").uppercase()
                if (mac.isBlank()) continue

                val ip = d.optString("qosListIP")
                val remark = d.optString("qosListRemark")
                val rawHostname = d.optString("qosListHostname")
                val displayName = if (remark.isNotBlank()) remark else rawHostname.ifBlank { "Unknown" }
                val connType = d.optString("qosListAccessType")

                // Live speeds (raw string from router, e.g. "0.00", "6.00")
                val dlSpeedRaw = d.optString("qosListDownSpeed", "0.00").toFloatOrNull() ?: 0f
                val ulSpeedRaw = d.optString("qosListUpSpeed", "0.00").toFloatOrNull() ?: 0f

                // Bandwidth limits (raw string from router, e.g. "256.00", "512.00", "38528.00")
                val dlLimitRaw = d.optString("qosListDownLimit", "38528.00").toFloatOrNull() ?: DeviceBandwidthInfo.NO_LIMIT_RAW
                val ulLimitRaw = d.optString("qosListUpLimit", "38528.00").toFloatOrNull() ?: DeviceBandwidthInfo.NO_LIMIT_RAW

                // Internet access status ("true" / "false")
                // Device is blocked if qosListAccess == "false", or in macFilterList
                val isLocal = isLocalDevice(mac, ip)
                val accessStr = d.optString("qosListAccess", "true")
                val internetEnabled = isLocal || (accessStr.equals("true", ignoreCase = true) &&
                        mac !in blockedMacs &&
                        (dlLimitRaw > 0f || dlLimitRaw >= DeviceBandwidthInfo.NO_LIMIT_RAW))

                result.add(
                    DeviceBandwidthInfo(
                        macAddress = mac,
                        hostname = displayName,
                        rawHostname = rawHostname,
                        remark = remark,
                        ipAddress = ip,
                        connectionType = connType,
                        downloadSpeedRaw = dlSpeedRaw,
                        uploadSpeedRaw = ulSpeedRaw,
                        downloadLimitRaw = dlLimitRaw,
                        uploadLimitRaw = ulLimitRaw,
                        internetAccessEnabled = internetEnabled,
                        isLocal = isLocal,
                    )
                )
            }

            // Also include any blocked devices in macFilterList that are not currently in onlineList
            if (macFilterList != null) {
                for (i in 0 until macFilterList.length()) {
                    val entry = macFilterList.optJSONObject(i) ?: continue
                    val mac = entry.optString("mac").uppercase()
                    if (mac.isBlank() || result.any { it.macAddress.equals(mac, ignoreCase = true) }) continue
                    val rawHostname = entry.optString("hostname")
                    val remark = entry.optString("remark")
                    val displayName = if (remark.isNotBlank()) remark else rawHostname.ifBlank { "Blocked Device" }
                    result.add(
                        DeviceBandwidthInfo(
                            macAddress = mac,
                            hostname = displayName,
                            rawHostname = rawHostname,
                            remark = remark,
                            ipAddress = "—",
                            connectionType = "—",
                            downloadSpeedRaw = 0f,
                            uploadSpeedRaw = 0f,
                            downloadLimitRaw = DeviceBandwidthInfo.NO_LIMIT_RAW,
                            uploadLimitRaw = DeviceBandwidthInfo.NO_LIMIT_RAW,
                            internetAccessEnabled = false,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseDeviceBandwidthList error: ${e.message}")
        }
        return result
    }

    /**
     * Applies bandwidth settings and internet access for ALL devices in a single batch request.
     *
     * This matches how the Tenda F3 web UI (net-control.js) works:
     * POST to /goform/setQos with:
     *   module1=onlineList&onlineList=<online_entries>&module2=macFilter&macFilterList=<offline_entries>
     *
     * Each entry is tab-separated and newline-delimited:
     *   hostname\tremark\tmac\tupLimit\tdownLimit\taccess
     */
    suspend fun applyAllBandwidthSettings(devices: List<DeviceBandwidthInfo>): Boolean =
        withContext(Dispatchers.IO) {
            val base = baseUrl()
            return@withContext try {
                val onlineEntries = devices.filter { it.internetAccessEnabled }.joinToString("\n") { d ->
                    val hostname = d.rawHostname.ifBlank { "Unknown" }
                    val remark = if (d.remark == hostname) "" else d.remark
                    val upLimit = if (d.uploadLimitRaw >= 38401f || d.uploadLimitRaw <= 0f) "38528" else d.uploadLimitRaw.toInt().toString()
                    val downLimit = if (d.downloadLimitRaw >= 38401f || d.downloadLimitRaw <= 0f) "38528" else d.downloadLimitRaw.toInt().toString()
                    "$hostname\t$remark\t${d.macAddress}\t$upLimit\t$downLimit\ttrue"
                }

                val blockedEntries = devices.filter { !it.internetAccessEnabled }.joinToString("\n") { d ->
                    val hostname = d.rawHostname.ifBlank { "Unknown" }
                    val remark = if (d.remark == hostname) "" else d.remark
                    "$hostname\t$remark\t${d.macAddress}\t0\t0\tfalse"
                }

                Log.d(TAG, "applyAllBandwidthSettings onlineList: $onlineEntries")
                Log.d(TAG, "applyAllBandwidthSettings macFilterList: $blockedEntries")

                val body = FormBody.Builder()
                    .add("module1", "onlineList")
                    .add("onlineList", onlineEntries)
                    .add("module2", "macFilter")
                    .add("macFilterList", blockedEntries)
                    .build()
                val requestBuilder = Request.Builder()
                    .url("$base/goform/setQos")
                    .post(body)
                sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
                val response = client.newCall(requestBuilder.build()).execute()
                val responseBody = response.body?.string() ?: ""
                if (BuildConfig.DEBUG) Log.d(TAG, "setQos response: $responseBody")
                response.isSuccessful || responseBody.contains("errCode\":0") ||
                    responseBody.contains("errCode\":\"0\"")
            } catch (e: Exception) {
                Log.e(TAG, "applyAllBandwidthSettings error: ${e.message}")
                false
            }
        }

    /**
     * Legacy per-device bandwidth limit — kept for compatibility.
     * Prefer applyAllBandwidthSettings() which matches the web UI behavior.
     */
    suspend fun setDeviceBandwidthLimit(
        mac: String,
        downloadLimitRaw: Float,
        uploadLimitRaw: Float,
    ): Boolean = withContext(Dispatchers.IO) {
        // Fetch current device list, update this device's limits, batch-apply
        return@withContext try {
            val current = getDeviceBandwidthList()
            if (current.isEmpty()) return@withContext false
            val updated = current.map { d ->
                if (d.macAddress.equals(mac, ignoreCase = true))
                    d.copy(downloadLimitRaw = downloadLimitRaw, uploadLimitRaw = uploadLimitRaw)
                else d
            }
            applyAllBandwidthSettings(updated)
        } catch (e: Exception) {
            Log.e(TAG, "setDeviceBandwidthLimit error: ${e.message}")
            false
        }
    }

    /**
     * Toggles internet access for a device via MAC filter.
     * enabled=true  → remove from blacklist (allow internet)
     * enabled=false → add to blacklist (block internet access)
     */
    suspend fun setDeviceInternetAccess(mac: String, enabled: Boolean): Boolean =
        if (enabled) unblockDevice(mac) else blockDevice(mac)

    // ── Block / Unblock Device ────────────────────────────────────────────────

    suspend fun blockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val json = postGoformWithParams(
                baseUrl(), "setMacFilterList",
                mapOf("mac" to mac, "type" to "1") // 1 = block
            )
            json != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unblockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val json = postGoformWithParams(
                baseUrl(), "setMacFilterList",
                mapOf("mac" to mac, "type" to "0") // 0 = allow
            )
            json != null
        } catch (e: Exception) {
            false
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun postGoformWithParams(
        base: String,
        action: String,
        params: Map<String, String>,
    ): String? {
        val bodyBuilder = FormBody.Builder().add("action", action)
        params.forEach { (k, v) -> bodyBuilder.add(k, v) }
        return executePost("$base/goform/$action", bodyBuilder.build())
    }

    private fun executePost(url: String, body: okhttp3.RequestBody): String? {
        val requestBuilder = Request.Builder().url(url).post(body)
        sessionCookie?.let { requestBuilder.addHeader("Cookie", it) }
        val response = client.newCall(requestBuilder.build()).execute()
        return if (response.isSuccessful) response.body?.string() else null
    }

    private fun formatUptime(seconds: Long): String {
        if (seconds <= 0L) return "—"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }

    fun clearSession() {
        sessionCookie = null
    }
}
