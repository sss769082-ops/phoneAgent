package com.example.agentapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class NgrokManager(private val context: Context) {

    private val TAG = "NgrokManager"
    private var ngrokProcess: Process? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun startTunnel(port: Int): String = withContext(Dispatchers.IO) {
        val ngrokBin = prepareNgrokBinary()
        val token = getSavedToken()

        if (token.isEmpty()) throw Exception("No ngrok authtoken set. Enter it in the app first.")

        Log.d(TAG, "Starting ngrok tunnel on port $port")

        // Kill any existing ngrok process
        ngrokProcess?.destroy()

        // Configure ngrok with auth token first
        val configProcess = ProcessBuilder(
            ngrokBin.absolutePath, "config", "add-authtoken", token
        ).apply {
            environment()["HOME"] = context.filesDir.absolutePath
        }.start()
        configProcess.waitFor()

        // Start the TCP tunnel
        ngrokProcess = ProcessBuilder(
            ngrokBin.absolutePath, "tcp", port.toString(),
            "--log=stdout",
            "--log-level=info"
        ).apply {
            environment()["HOME"] = context.filesDir.absolutePath
            redirectErrorStream(true)
        }.start()

        // Read output in background thread for debugging
        Thread {
            ngrokProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                Log.d(TAG, "ngrok: $line")
            }
        }.start()

        // Poll ngrok local API until tunnel URL is available
        var url: String? = null
        var attempts = 0
        while (url == null && attempts < 20) {
            delay(1500)
            attempts++
            try {
                url = fetchTunnelUrl()
                Log.d(TAG, "Got ngrok URL: $url after $attempts attempts")
            } catch (e: Exception) {
                Log.d(TAG, "Attempt $attempts: ${e.message}")
            }
        }

        url ?: throw Exception("ngrok did not start after ${attempts * 1.5}s. Check your authtoken.")
    }

    private fun fetchTunnelUrl(): String {
        val request = Request.Builder()
            .url("http://127.0.0.1:4040/api/tunnels")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response from ngrok API")
            val json = JSONObject(body)
            val tunnels = json.getJSONArray("tunnels")
            if (tunnels.length() == 0) throw Exception("No tunnels yet")

            val tunnel = tunnels.getJSONObject(0)
            // Returns something like "tcp://0.tcp.ngrok.io:12345"
            return tunnel.getString("public_url")
                .removePrefix("tcp://")
        }
    }

    private fun prepareNgrokBinary(): File {
        val ngrokFile = File(context.filesDir, "ngrok")

        // If already exists and executable, use it
        if (ngrokFile.exists() && ngrokFile.canExecute()) {
            Log.d(TAG, "Using existing ngrok binary")
            return ngrokFile
        }

        // Download ngrok binary for ARM64 Android (most modern Android devices)
        // ngrok provides static binaries — download the ARM64 Linux version
        Log.d(TAG, "Downloading ngrok binary...")
        val downloadUrl = "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-arm64.tgz"

        try {
            val tgzFile = File(context.filesDir, "ngrok.tgz")
            URL(downloadUrl).openStream().use { input ->
                FileOutputStream(tgzFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract using tar
            val extractProcess = ProcessBuilder("tar", "-xzf", tgzFile.absolutePath, "-C", context.filesDir.absolutePath)
                .start()
            extractProcess.waitFor()
            tgzFile.delete()

            if (!ngrokFile.exists()) throw Exception("Extraction failed")

        } catch (e: Exception) {
            // Fallback: try assets (if user bundled the binary)
            Log.w(TAG, "Download failed, trying assets: ${e.message}")
            try {
                context.assets.open("ngrok").use { input ->
                    FileOutputStream(ngrokFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (assetEx: Exception) {
                throw Exception("Could not get ngrok binary. Check internet connection. (${e.message})")
            }
        }

        ngrokFile.setExecutable(true, false)
        Log.d(TAG, "ngrok binary ready at ${ngrokFile.absolutePath}")
        return ngrokFile
    }

    private fun getSavedToken(): String {
        val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)
        return prefs.getString("ngrok_token", "") ?: ""
    }

    fun stop() {
        ngrokProcess?.destroy()
        ngrokProcess = null
        Log.d(TAG, "ngrok stopped")
    }
}
