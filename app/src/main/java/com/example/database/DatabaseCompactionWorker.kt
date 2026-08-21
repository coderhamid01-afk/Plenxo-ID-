package com.example.database

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DatabaseCompactionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DatabaseCompactionWorker", "Periodic SQLite Compaction starting...")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val writableDb = db.openHelper.writableDatabase
            writableDb.execSQL("VACUUM")
            // PRAGMA wal_checkpoint returns data, so we use query instead of execSQL to avoid SQLiteException
            // Using a safer query call for SupportSQLiteDatabase
            val cursor = writableDb.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray())
            cursor.moveToFirst()
            cursor.close()
            Log.d("DatabaseCompactionWorker", "SQLite Database compacted successfully (VACUUM and WAL TRUNCATE executed).")
            Result.success()
        } catch (e: Exception) {
            Log.e("DatabaseCompactionWorker", "Failed to run SQLite Database compaction", e)
            Result.failure()
        }
    }
}
