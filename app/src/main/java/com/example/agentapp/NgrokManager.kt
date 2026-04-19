package com.example.agentapp

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NgrokManager(private val context: Context) {

    private val TAG = "NgrokManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun startTunnel(port: Int): String = withContext(Dispatchers.IO) {
        val token = getSavedToken()

        // Always show local WiFi IP first (works on same network)
        val localIp = getWifiIp()
        val localUrl = "$localIp:$port"
        Log.d(TAG, "Local URL: $localUrl")

        // If no token, just return local IP
        if (token.isEmpty()) {
            return@withContext "LOCAL:$localUrl"
        }

        // Try to get public URL via ngrok API
        try {
            val publicUrl = tryNgrokApi(token, port)
            return@withContext "PUBLIC:$publicUrl|LOCAL:$localUrl"
        } catch (e: Exception) {
            Log.w(TAG, "Public tunnel failed: ${e.message}")
            // Return local IP as fallback
            return@withContext "LOCAL:$localUrl"
        }
    }

    private fun tryNgrokApi(apiKey: String, port: Int): String {
        // First delete any existing tunnel
        try {
            val delRequest = Request.Builder()
                .url("https://api.ngrok.com/tunnels/phoneagent")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Ngrok-Version", "2")
                .delete()
                .build()
            client.newCall(delRequest).execute().close()
        } catch (e: Exception) { /* ignore */ }

        Thread.sleep(1000)

        // Create tunnel
        val body = JSONObject().apply {
            put("addr", "localhost:$port")
            put("proto", "tcp")
            put("name", "phoneagent")
        }.toString()

        val request = Request.Builder()
            .url("https://api.ngrok.com/tunnels")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Ngrok-Version", "2")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "ngrok API (${response.code}): $respBody")

            if (!response.isSuccessful) {
                val msg = try { JSONObject(respBody).optString("msg", respBody) }
                          catch (e: Exception) { respBody }
                throw Exception(msg)
            }

            val json = JSONObject(respBody)
            val url = json.optString("public_url", "")
            if (url.isEmpty()) throw Exception("No public_url in response")
            return url.removePrefix("tcp://")
        }
    }

    private fun getWifiIp(): String {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        } catch (e: Exception) {
            return "unknown"
        }
    }

    fun stop() {}

    private fun getSavedToken(): String {
        val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
        return prefs.getString("ngrok_token", "") ?: ""
    }
}
