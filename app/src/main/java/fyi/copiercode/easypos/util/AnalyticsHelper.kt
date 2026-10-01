package fyi.copiercode.easypos.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.*
import java.io.IOException
import java.net.NetworkInterface
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AnalyticsHelper {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private const val HEARTBEAT_URL = "https://app.copiercode.fyi/api/device_heartbeat"
    private const val COMPLAINT_URL = "https://app.copiercode.fyi/api/complaint"

    @Serializable
    data class HeartbeatResponse(
        val status: String,
        val is_approved: Boolean = false
    )

    fun sendDeviceHeartbeat(context: Context, onResult: (Boolean?) -> Unit) {
        val deviceId = getDeviceId(context)
        val appVersion = "10"
        val deviceModel = Build.MODEL
        val ipAddress = getLocalIpAddress()
        val macAddress = getMacAddress()

        val formBody = FormBody.Builder()
            .add("device_id", deviceId)
            .add("app_version", appVersion)
            .add("device_model", deviceModel)
            .add("ip_address", ipAddress)
            .add("mac_address", macAddress)
            .build()

        val request = Request.Builder()
            .url(HEARTBEAT_URL)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                try {
                    val res = json.decodeFromString<HeartbeatResponse>(body ?: "")
                    onResult(res.is_approved)
                } catch (e: Exception) {
                    onResult(null)
                } finally {
                    response.close()
                }
            }
        })
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
    }

    fun sendComplaint(context: Context, complaintText: String) {
        val deviceId = getDeviceId(context)
        val timestamp = System.currentTimeMillis().toString()

        val formBody = FormBody.Builder()
            .add("device_id", deviceId)
            .add("complaint_text", complaintText)
            .add("timestamp", timestamp)
            .build()

        val request = Request.Builder()
            .url(COMPLAINT_URL)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val host = address.hostAddress ?: continue
                    if (!address.isLoopbackAddress && host.indexOf(':') < 0) {
                        return host
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "0.0.0.0"
    }

    fun getMacAddress(): String {
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true)) continue

                val macBytes = nif.hardwareAddress ?: return "02:00:00:00:00:00"
                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }
                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                return res1.toString()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "02:00:00:00:00:00"
    }
}
