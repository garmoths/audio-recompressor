package com.serverihamyaptim.audiocompressor.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "encode_history", primaryKeys = ["jobId"])
data class HistoryItem(
    val jobId: String,
    val sourceName: String,
    val outputName: String,
    val outputUri: String,
    val format: String,
    val quality: String,
    val iterations: Int,
    val inputBytes: Long,
    val outputBytes: Long,
    val elapsedMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao interface HistoryDao {
    @Query("SELECT * FROM encode_history ORDER BY createdAt DESC") fun observeAll(): Flow<List<HistoryItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: HistoryItem)
    @Query("DELETE FROM encode_history WHERE jobId = :jobId") suspend fun delete(jobId: String)
}

@Database(entities = [HistoryItem::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    companion object {
        @Volatile private var instance: HistoryDatabase? = null
        fun get(context: Context): HistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, HistoryDatabase::class.java, "history.db").build()
                .also { instance = it }
        }
    }
}
