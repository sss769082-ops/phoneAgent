package com.example.agentapp

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Base64
import androidx.core.content.ContextCompat
import android.Manifest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DataCollector(private val context: Context) {

    fun getContacts(): JSONArray {
        val result = JSONArray()
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return errorArray("Contacts permission not granted. Go to Settings > Apps > Phone Agent > Permissions > Contacts > Allow")
        }
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    result.put(JSONObject().apply {
                        put("name", it.getString(0) ?: "")
                        put("number", it.getString(1) ?: "")
                        put("type", phoneTypeLabel(it.getInt(2)))
                    })
                }
            }
        } catch (e: Exception) {
            return errorArray("Error reading contacts: ${e.message}")
        }
        return result
    }

    fun getCallLog(limit: Int = 50): JSONArray {
        val result = JSONArray()
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return errorArray("Call log permission not granted. Go to Settings > Apps > Phone Agent > Permissions > Phone > Allow")
        }
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            var count = 0
            cursor?.use {
                while (it.moveToNext() && count < limit) {
                    val typeInt = it.getInt(2)
                    result.put(JSONObject().apply {
                        put("name", it.getString(0) ?: "Unknown")
                        put("number", it.getString(1) ?: "")
                        put("type", callTypeLabel(typeInt))
                        put("date", it.getLong(3))
                        put("date_readable", java.text.SimpleDateFormat("dd MMM yyyy HH:mm",
                            java.util.Locale.getDefault()).format(java.util.Date(it.getLong(3))))
                        put("duration_sec", it.getInt(4))
                    })
                    count++
                }
            }
        } catch (e: Exception) {
            return errorArray("Error reading call log: ${e.message}")
        }
        return result
    }

    fun getSMS(limit: Int = 50): JSONArray {
        val result = JSONArray()

        // On Android 12+, READ_SMS requires being default SMS app
        // We try anyway and gracefully handle the error
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            return errorArray("SMS permission not granted. On Android 12+, go to Settings > Apps > Default Apps > SMS > select Phone Agent temporarily")
        }

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC"
            )
            var count = 0
            cursor?.use {
                while (it.moveToNext() && count < limit) {
                    val typeInt = it.getInt(3)
                    result.put(JSONObject().apply {
                        put("address", it.getString(0) ?: "")
                        put("body", it.getString(1) ?: "")
                        put("date", it.getLong(2))
                        put("date_readable", java.text.SimpleDateFormat("dd MMM yyyy HH:mm",
                            java.util.Locale.getDefault()).format(java.util.Date(it.getLong(2))))
                        put("type", if (typeInt == Telephony.Sms.MESSAGE_TYPE_SENT) "sent" else "received")
                    })
                    count++
                }
            }
        } catch (e: SecurityException) {
            return errorArray("SMS blocked by Android. Set Phone Agent as default SMS app in Settings > Default Apps > SMS App")
        } catch (e: Exception) {
            return errorArray("Error reading SMS: ${e.message}")
        }
        return result
    }

    fun getInstalledApps(): JSONArray {
        val result = JSONArray()
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            packages
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { it.loadLabel(pm).toString() }
                .forEach { info ->
                    result.put(JSONObject().apply {
                        put("name", info.loadLabel(pm).toString())
                        put("package", info.packageName)
                    })
                }
        } catch (e: Exception) {
            return errorArray("Error reading apps: ${e.message}")
        }
        return result
    }

    fun getLastPhoto(): String? {
        val file = File(context.filesDir, "last_photo.jpg")
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    fun getLastScreen(): String? {
        val file = File(context.filesDir, "last_screen.jpg")
        if (!file.exists()) return null
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun errorArray(msg: String): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("error", true)
                put("message", msg)
            })
        }
    }

    private fun phoneTypeLabel(type: Int) = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME   -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK   -> "work"
        else -> "other"
    }

    private fun callTypeLabel(type: Int) = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE   -> "missed"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        else -> "unknown"
    }
}
