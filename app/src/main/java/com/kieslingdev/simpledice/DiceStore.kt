package com.kieslingdev.simpledice

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Durable storage for the complete roll archive, the visible recent subset, and roll settings. */
class DiceStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), java.io.Closeable {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE_ROLLS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SIDES INTEGER NOT NULL,
                $COLUMN_VALUE INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_RECENT INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE $TABLE_SETTINGS (
                $COLUMN_SETTINGS_KEY TEXT PRIMARY KEY,
                $COLUMN_SETTINGS_VALUE TEXT NOT NULL
            )""".trimIndent()
        )
        putSetting(db, KEY_SELECTED_DIE, "20")
        putSetting(db, KEY_LAST_ROLL_ID, "0")
        putSetting(db, KEY_MODE, RollMode.INSTANT.name)
        putSetting(db, KEY_DURATION_STEPS, "2")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first durable schema. Future versions should migrate in place here.
    }

    @Synchronized
    fun loadState(): DiceState {
        val db = readableDatabase
        val selectedDie = getSetting(db, KEY_SELECTED_DIE)?.toIntOrNull()?.takeIf { it in DICE } ?: 20
        val lastRollId = getSetting(db, KEY_LAST_ROLL_ID)?.toLongOrNull() ?: 0L
        val archive = mutableListOf<Roll>()
        val recent = mutableListOf<Roll>()
        db.query(
            TABLE_ROLLS,
            arrayOf(COLUMN_ID, COLUMN_SIDES, COLUMN_VALUE, COLUMN_TIMESTAMP, COLUMN_RECENT),
            null,
            null,
            null,
            null,
            "$COLUMN_ID DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val roll = Roll(
                    sides = cursor.getInt(1),
                    value = cursor.getInt(2),
                    id = cursor.getLong(0),
                    timestamp = cursor.getLong(3)
                )
                archive += roll
                if (cursor.getInt(4) != 0) recent += roll
            }
        }
        seedRollIdSequence(maxOf(lastRollId, archive.maxOfOrNull { it.id } ?: 0L))
        return DiceState(selectedDie, recent.take(RECENT_ROLLS_SIZE), archive)
    }

    @Synchronized
    fun loadSettings(): RollSettings {
        val db = readableDatabase
        val mode = getSetting(db, KEY_MODE)?.let { runCatching { RollMode.valueOf(it) }.getOrNull() }
            ?: RollMode.INSTANT // Old HOLD settings become instant taps with hold-to-animate.
        val steps = getSetting(db, KEY_DURATION_STEPS)?.toIntOrNull()?.takeIf { it in 1..10 } ?: 2
        return RollSettings(mode, steps, getSetting(db, KEY_DARK_MODE) == "true")
    }

    @Synchronized
    fun saveSettings(settings: RollSettings) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            putSetting(db, KEY_MODE, settings.mode.name)
            putSetting(db, KEY_DURATION_STEPS, settings.durationSteps.toString())
            putSetting(db, KEY_DARK_MODE, settings.darkMode.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun recordRoll(roll: Roll, selectedDie: Int): Roll {
        require(selectedDie in DICE)
        val db = writableDatabase
        db.beginTransaction()
        try {
            putSetting(db, KEY_SELECTED_DIE, selectedDie.toString())
            val previousMaxId = getSetting(db, KEY_LAST_ROLL_ID)?.toLongOrNull() ?: 0L
            val values = ContentValues().apply {
                if (roll.id > previousMaxId) put(COLUMN_ID, roll.id)
                put(COLUMN_SIDES, roll.sides)
                put(COLUMN_VALUE, roll.value)
                put(COLUMN_TIMESTAMP, roll.timestamp)
                put(COLUMN_RECENT, 1)
            }
            val id = db.insertOrThrow(TABLE_ROLLS, null, values)
            putSetting(db, KEY_LAST_ROLL_ID, maxOf(previousMaxId, id).toString())
            trimRecent(db)
            db.setTransactionSuccessful()
            return if (roll.id == id) roll else roll.copy(id = id)
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveSelectedDie(selectedDie: Int) {
        require(selectedDie in DICE)
        val db = writableDatabase
        db.beginTransaction()
        try {
            putSetting(db, KEY_SELECTED_DIE, selectedDie.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Hides every result from recent history while retaining all rows in the archive. */
    @Synchronized
    fun clearRecent() {
        writableDatabase.update(TABLE_ROLLS, ContentValues().apply { put(COLUMN_RECENT, 0) }, null, null)
    }

    @Synchronized
    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ROLLS, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes only the selected rows. Older archived results are not promoted into recent history. */
    @Synchronized
    fun deleteRolls(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.chunked(MAX_SQL_PARAMETERS).forEach { batch ->
                val placeholders = batch.joinToString(",") { "?" }
                db.delete(TABLE_ROLLS, "$COLUMN_ID IN ($placeholders)", batch.map(Long::toString).toTypedArray())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun trimRecent(db: SQLiteDatabase) {
        val overflow = mutableListOf<Long>()
        db.rawQuery(
            "SELECT $COLUMN_ID FROM $TABLE_ROLLS WHERE $COLUMN_RECENT = 1 " +
                "ORDER BY $COLUMN_ID DESC LIMIT -1 OFFSET $RECENT_ROLLS_SIZE",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) overflow += cursor.getLong(0)
        }
        overflow.forEach { id ->
            db.update(
                TABLE_ROLLS,
                ContentValues().apply { put(COLUMN_RECENT, 0) },
                "$COLUMN_ID = ?",
                arrayOf(id.toString())
            )
        }
    }

    private fun putSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            ContentValues().apply {
                put(COLUMN_SETTINGS_KEY, key)
                put(COLUMN_SETTINGS_VALUE, value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun getSetting(db: SQLiteDatabase, key: String): String? =
        db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_SETTINGS_VALUE),
            "$COLUMN_SETTINGS_KEY = ?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    companion object {
        private const val DATABASE_NAME = "dice_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_ROLLS = "rolls"
        private const val TABLE_SETTINGS = "settings"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SIDES = "sides"
        private const val COLUMN_VALUE = "value"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_RECENT = "is_recent"
        private const val COLUMN_SETTINGS_KEY = "key"
        private const val COLUMN_SETTINGS_VALUE = "value"
        private const val KEY_SELECTED_DIE = "selected_die"
        private const val KEY_LAST_ROLL_ID = "last_roll_id"
        private const val KEY_MODE = "roll_mode"
        private const val KEY_DURATION_STEPS = "duration_steps"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val MAX_SQL_PARAMETERS = 900
    }
}
