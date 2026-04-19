package com.example.agentapp

import android.content.Context
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val NGROK_API = "https://api.ngrok.com"

    suspend fun startTunnel(port: Int): String = withContext(Dispatchers.IO) {
        val token = getSavedToken()
        if (token.isEmpty()) throw Exception("No ngrok authtoken set.")

        Log.d(TAG, "Creating ngrok tunnel via API for port $port...")

        // Delete old tunnel first (ignore errors)
        try { deleteTunnel(token) } catch (e: Exception) {}

        // Create new tunnel
        val url = createTunnel(token, port)
        Log.d(TAG, "Tunnel ready: $url")
        url
    }

    private fun createTunnel(token: String, port: Int): String {
        val jsonBody = JSONObject().apply {
            put("addr", port.toString())
            put("proto", "tcp")
            put("name", "phoneagent")
        }.toString()

        val request = Request.Builder()
            .url("$NGROK_API/tunnels")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Ngrok-Version", "2")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            Log.d(TAG, "Create tunnel response (${response.code}): $body")

            if (!response.isSuccessful) {
                val msg = try { JSONObject(body).optString("msg", body) } catch (e: Exception) { body }
                throw Exception("ngrok error: $msg")
            }

            val json = JSONObject(body)
            val publicUrl = json.optString("public_url", "")
            if (publicUrl.isEmpty()) {
                // Try nested structure
                val forwards = json.optJSONObject("forwards_to") ?: throw Exception("No URL in: $body")
                throw Exception("Unexpected response format: $body")
            }
            return publicUrl.removePrefix("tcp://")
        }
    }

    private fun deleteTunnel(token: String) {
        val request = Request.Builder()
            .url("$NGROK_API/tunnels/phoneagent")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Ngrok-Version", "2")
            .delete()
            .build()
        client.newCall(request).execute().close()
    }

    fun stop() {
        val token = getSavedToken()
        if (token.isEmpty()) return
        try { deleteTunnel(token) } catch (e: Exception) {
            Log.w(TAG, "Stop: ${e.message}")
        }
    }

    private fun getSavedToken(): String {
        val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
        return prefs.getString("ngrok_token", "") ?: ""
    }
}
