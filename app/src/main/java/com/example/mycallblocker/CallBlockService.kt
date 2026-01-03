package com.example.mycallblocker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallBlockService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = getPhoneNumber(callDetails)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dbHelper = CallLogDbHelper(this)

        val isInterceptionActive = prefs.getBoolean(PREF_INTERCEPTION_ACTIVE, false)
        val isWhitelistEnabled = prefs.getBoolean(PREF_CONTACT_WHITELIST_ENABLED, false)

        var shouldBlock = false
        var reason = ""

        // 逻辑判断
        if (!isInterceptionActive) {
            shouldBlock = false
            reason = "服务暂停(用户关闭)"
        } else {
            if (isWhitelistEnabled) {
                // 检查权限
                val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_CONTACTS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPerm) {
                    shouldBlock = false
                    reason = "无通讯录权限(强制放行)"
                } else if (contactExists(this, phoneNumber)) {
                    shouldBlock = false
                    reason = "通讯录好友"
                } else {
                    shouldBlock = true
                    reason = "陌生号码拦截"
                }
            } else {
                shouldBlock = true
                reason = "全员拦截模式"
            }
        }

        // 写日志
        val actionStr = if (shouldBlock) "已拦截" else "已放行"
        dbHelper.addRecord(phoneNumber, actionStr, reason)
        Log.d("CallBlocker", "电话:$phoneNumber 结果:$actionStr")

        // 执行操作
        if (shouldBlock) {
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
            respondToCall(callDetails, response)
        } else {
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }

    private fun getPhoneNumber(callDetails: Call.Details): String {
        return callDetails.handle?.schemeSpecificPart ?: "未知"
    }

    private fun contactExists(context: Context, number: String): Boolean {
        if (number.isEmpty()) return false
        val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
            if (cursor != null && cursor.count > 0) return true
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return false
    }
}