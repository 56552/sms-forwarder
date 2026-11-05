package com.ghajni.smsforwarder.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SmsLogDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_LOGS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SENDER TEXT,
                $COL_BODY TEXT,
                $COL_FORWARDED_TO TEXT,
                $COL_TIMESTAMP INTEGER,
                $COL_STATUS TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGS")
        onCreate(db)
    }

    fun insertLog(item: SmsLogItem): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SENDER, item.sender)
            put(COL_BODY, item.body)
            put(COL_FORWARDED_TO, item.forwardedTo)
            put(COL_TIMESTAMP, item.timestamp)
            put(COL_STATUS, item.status)
        }
        return db.insert(TABLE_LOGS, null, values)
    }

    fun getAllLogs(limit: Int = 100): List<SmsLogItem> {
        val list = mutableListOf<SmsLogItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(COL_SENDER))
                    val body = it.getString(it.getColumnIndexOrThrow(COL_BODY))
                    val forwardedTo = it.getString(it.getColumnIndexOrThrow(COL_FORWARDED_TO))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                    val status = it.getString(it.getColumnIndexOrThrow(COL_STATUS))

                    list.add(SmsLogItem(id, sender, body, forwardedTo, timestamp, status))
                } while (it.moveToNext())
            }
        }
        return list
    }

    fun clearAllLogs() {
        val db = writableDatabase
        db.delete(TABLE_LOGS, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "sms_forwarder.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_LOGS = "sms_logs"
        const val COL_ID = "id"
        const val COL_SENDER = "sender"
        const val COL_BODY = "body"
        const val COL_FORWARDED_TO = "forwarded_to"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_STATUS = "status"
    }
}

