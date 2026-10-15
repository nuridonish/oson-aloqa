package com.osonaloqa.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(val id: Long, val address: String, val body: String, val ts: Long, val out: Boolean, val status: Int, val kind: String = "text")
data class Conv(val address: String, val lastBody: String, val ts: Long, val out: Boolean)

object MessageStore {

    private lateinit var db: SQLiteDatabase

    fun init(context: Context) {
        if (::db.isInitialized) return
        db = Helper(context.applicationContext).writableDatabase
    }

    fun insert(address: String, body: String, ts: Long, out: Boolean, status: Int): Long {
        val cv = ContentValues().apply {
            put("address", address)
            put("body", body)
            put("ts", ts)
            put("out", if (out) 1 else 0)
            put("status", status)
        }
        return db.insert("messages", null, cv)
    }

    fun setStatus(id: Long, status: Int) {
        db.update("messages", ContentValues().apply { put("status", status) }, "_id=?", arrayOf(id.toString()))
    }

    fun conversations(): List<Conv> {
        val out = ArrayList<Conv>()
        db.rawQuery(
            "SELECT m.address, m.body, m.ts, m.out FROM messages m WHERE m.ts = (SELECT MAX(ts) FROM messages WHERE address = m.address) ORDER BY m.ts DESC",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(Conv(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3) == 1))
        }
        return out
    }

    fun messages(address: String): List<Msg> {
        val out = ArrayList<Msg>()
        db.rawQuery(
            "SELECT _id, address, body, ts, out, status FROM messages WHERE address=? ORDER BY ts ASC",
            arrayOf(address)
        ).use { c ->
            while (c.moveToNext()) out.add(Msg(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) == 1, c.getInt(5)))
        }
        return out
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "osonaloqa.db", null, 1) {

        override fun onCreate(d: SQLiteDatabase) {
            d.execSQL("CREATE TABLE messages(_id INTEGER PRIMARY KEY AUTOINCREMENT, address TEXT NOT NULL, body TEXT NOT NULL, ts INTEGER NOT NULL, out INTEGER NOT NULL, status INTEGER NOT NULL DEFAULT 0)")
            d.execSQL("CREATE INDEX idx_addr_ts ON messages(address, ts)")
        }

        override fun onUpgrade(d: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            d.execSQL("DROP TABLE IF EXISTS messages")
            onCreate(d)
        }
    }
}

