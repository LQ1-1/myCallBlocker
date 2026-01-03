package com.example.mycallblocker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

// 数据模型
data class CallRecord(
    val id: Long,
    val number: String,
    val time: Long,
    val action: String,
    val reason: String
)

class CallLogDbHelper(context: Context) : SQLiteOpenHelper(context, "CallLog.db", null, 2) { // 版本号设为2以防万一

    override fun onCreate(db: SQLiteDatabase) {
        // 使用 call_action 避免关键字冲突
        db.execSQL(
            "CREATE TABLE logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "number TEXT, " +
                    "time INTEGER, " +
                    "call_action TEXT, " +
                    "reason TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS logs")
        onCreate(db)
    }

    fun addRecord(number: String, action: String, reason: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("number", number)
            put("time", System.currentTimeMillis())
            put("call_action", action)
            put("reason", reason)
        }
        db.insert("logs", null, values)
        db.close()
    }

    // ✅ 分页查询：每次只查 pageSize 条
    fun getRecordsPage(limit: Int, offset: Int): List<CallRecord> {
        val list = mutableListOf<CallRecord>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM logs ORDER BY time DESC LIMIT ? OFFSET ?",
            arrayOf(limit.toString(), offset.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    CallRecord(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getString(3),
                        cursor.getString(4)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // 批量删除
    fun deleteBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val args = ids.joinToString(",")
        db.execSQL("DELETE FROM logs WHERE id IN ($args)")
        db.close()
    }

    // 清空所有
    fun deleteAllRecords() {
        val db = writableDatabase
        db.delete("logs", null, null)
        db.close()
    }

    // 按月删除
    fun deleteByMonth(year: Int, month: Int): Int {
        val start = Calendar.getInstance().apply { set(year, month - 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val end = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.MONTH, 1) }
        return deleteByTimeRange(start.timeInMillis, end.timeInMillis)
    }

    // 按年删除
    fun deleteByYear(year: Int): Int {
        val start = Calendar.getInstance().apply { set(year, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val end = Calendar.getInstance().apply { set(year + 1, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        return deleteByTimeRange(start.timeInMillis, end.timeInMillis)
    }

    private fun deleteByTimeRange(start: Long, end: Long): Int {
        val db = writableDatabase
        val count = db.delete("logs", "time >= ? AND time < ?", arrayOf(start.toString(), end.toString()))
        db.close()
        return count
    }
}