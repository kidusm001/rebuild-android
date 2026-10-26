package com.rebuild.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rebuild.domain.Seed
import java.io.Closeable

internal class SqliteSnapshotPersistence(
    context: Context,
    name: String = "rebuild.db"
) : SQLiteOpenHelper(context, name, null, 1, DatabaseErrorHandler {
    // Android's default corruption handler deletes the database; keeping the file is intentional.
    throw RepositoryException("The database is damaged. It has not been deleted or reset. Keep a copy and restore a verified backup.")
}), SnapshotPersistence, Closeable {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE snapshot (id INTEGER PRIMARY KEY CHECK(id = 1), payload TEXT NOT NULL)")
        val values = ContentValues().apply {
            put("id", 1)
            put("payload", SnapshotCodec.encode(Seed.initialState()))
        }
        db.insertOrThrow("snapshot", null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw RepositoryException("This database needs a migration from version $oldVersion to $newVersion. Your data has not been reset.")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw RepositoryException("This database was created by a newer Rebuild version. Update the app; your data has not been reset.")
    }

    override fun read(): String {
        val db = readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val characterCount = db.rawQuery(
                "SELECT length(payload), instr(payload, char(0)) FROM snapshot WHERE id = 1", null
            ).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) {
                    throw RepositoryException("The saved snapshot is missing. The database has not been reset.")
                }
                SnapshotCodec.ensure(cursor.getLong(1) == 0L,
                    "The saved snapshot contains invalid text. The database has not been reset.")
                val length = cursor.getLong(0)
                SnapshotCodec.ensure(length > 0, "The saved snapshot is empty. The database has not been reset.")
                SnapshotCodec.ensure(length <= SnapshotCodec.MAX_BYTES,
                    "The saved snapshot exceeds the 10 MiB size limit. The database has not been reset.")
                length.toInt()
            }
            val result = StringBuilder(minOf(characterCount, READ_CHUNK_CODE_POINTS))
            var offset = 1
            var utf8Bytes = 0
            while (offset <= characterCount) {
                val count = minOf(READ_CHUNK_CODE_POINTS, characterCount - offset + 1)
                val chunk = db.rawQuery(
                    "SELECT substr(payload, ?, ?) FROM snapshot WHERE id = 1",
                    arrayOf(offset.toString(), count.toString())
                ).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) {
                        throw RepositoryException("The saved snapshot could not be read completely. The database has not been reset.")
                    }
                    cursor.getString(0)
                }
                // SQLite counts Unicode code points; String.length counts UTF-16 code units.
                SnapshotCodec.ensure(chunk.codePointCount(0, chunk.length) == count,
                    "The saved snapshot could not be read completely. The database has not been reset.")
                utf8Bytes += chunk.toByteArray(Charsets.UTF_8).size
                SnapshotCodec.ensure(utf8Bytes <= SnapshotCodec.MAX_BYTES,
                    "The saved snapshot exceeds the 10 MiB size limit. The database has not been reset.")
                result.append(chunk)
                offset += count
            }
            db.setTransactionSuccessful()
            return result.toString()
        } finally {
            db.endTransaction()
        }
    }

    override fun write(snapshot: String) {
        SnapshotCodec.decode(snapshot)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply { put("payload", snapshot) }
            if (db.update("snapshot", values, "id = ?", arrayOf("1")) != 1) {
                throw RepositoryException("The saved snapshot is missing. Changes were not written.")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        // At most 64 KiB UTF-8 (or 64 KiB UTF-16) per CursorWindow row, including astral characters.
        const val READ_CHUNK_CODE_POINTS = 16 * 1024
    }
}
