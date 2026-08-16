package com.example.aiassistant

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversation_history")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val persona: String,
    val sender: String, // "USER" or "ASSISTANT"
    val content: String,
    val tags: String = "" // e.g., "preference", "project", "routine"
)

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey val key: String,
    val value: String,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entry: MemoryEntry)

    @Query("SELECT * FROM conversation_history ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 10): List<MemoryEntry>

    @Query("SELECT * FROM conversation_history WHERE content LIKE '%' || :query || '%' ORDER BY id DESC LIMIT 5")
    suspend fun searchMemories(query: String): List<MemoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(pref: UserPreference)

    @Query("SELECT value FROM user_preferences WHERE `key` = :key")
    suspend fun getPreference(key: String): String?

    @Query("SELECT * FROM user_preferences")
    suspend fun getAllPreferences(): List<UserPreference>
}

@Database(entities = [MemoryEntry::class, UserPreference::class], version = 1, exportSchema = false)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var INSTANCE: AssistantDatabase? = null

        fun getDatabase(context: Context): AssistantDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AssistantDatabase::class.java,
                    "jarvis_longterm_memory.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
