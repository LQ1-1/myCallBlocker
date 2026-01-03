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

        // 核心修改：不再查列表，而是查通讯录
        if (contactExists(this, phoneNumber)) {
            // ---> 是通讯录好友，放行
            Log.d("CallBlocker", "通讯录好友，放行: $phoneNumber")
            respondToCall(callDetails, CallResponse.Builder().build())
        } else {
            // ---> 陌生人，拦截
            Log.d("CallBlocker", "陌生号码，拦截: $phoneNumber")
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()

            respondToCall(callDetails, response)
        }
    }

    private fun getPhoneNumber(callDetails: Call.Details): String {
        return callDetails.handle?.schemeSpecificPart ?: ""
    }

    // 新增：查询号码是否在通讯录中
    private fun contactExists(context: Context, number: String): Boolean {
        if (number.isEmpty()) return false

        // 为了提高匹配率，最好只取后几位（比如后7位或后9位）进行模糊匹配
        // 这里演示标准查询
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
            if (cursor != null && cursor.count > 0) {
                return true // 查到了，说明在通讯录里
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return false
    }
}