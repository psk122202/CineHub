package com.cinehub.tv.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
)

/** Настроенный источник: сервер Jellyfin/Plex, плейлист, SMB-папка. */
@Entity(tableName = "sources")
data class SourceConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val userId: String = "",
    val enabled: Boolean = true,
)

/** Избранное и «Буду смотреть». list = "fav" | "watchlist". */
@Entity(tableName = "library", primaryKeys = ["profileId", "list", "key"])
data class LibraryEntry(
    val profileId: Long,
    val list: String,
    val key: String,
    val itemJson: String,
    val addedAt: Long,
)

/** Прогресс просмотра (для «Продолжить просмотр» и истории). */
@Entity(tableName = "progress", primaryKeys = ["profileId", "key"])
data class WatchProgress(
    val profileId: Long,
    val key: String,
    val itemJson: String,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
)

/** Ссылка на видео, добавленная вручную. */
@Entity(tableName = "links")
data class UserLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val poster: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun all(): List<Profile>

    @Insert
    suspend fun insert(p: Profile): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observeAll(): Flow<List<SourceConfig>>

    @Query("SELECT * FROM sources ORDER BY id")
    suspend fun all(): List<SourceConfig>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SourceConfig): Long

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library WHERE profileId = :profileId AND list = :list ORDER BY addedAt DESC")
    fun observe(profileId: Long, list: String): Flow<List<LibraryEntry>>

    @Query("SELECT COUNT(*) FROM library WHERE profileId = :profileId AND list = :list AND `key` = :key")
    fun observeContains(profileId: Long, list: String, key: String): Flow<Int>

    @Upsert
    suspend fun upsert(e: LibraryEntry)

    @Query("DELETE FROM library WHERE profileId = :profileId AND list = :list AND `key` = :key")
    suspend fun delete(profileId: Long, list: String, key: String)

    @Query("DELETE FROM library WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE profileId = :profileId AND finished = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT 30")
    fun observeContinue(profileId: Long): Flow<List<WatchProgress>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT 300")
    fun observeHistory(profileId: Long): Flow<List<WatchProgress>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND `key` = :key")
    suspend fun get(profileId: Long, key: String): WatchProgress?

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND `key` = :key")
    fun observe(profileId: Long, key: String): Flow<WatchProgress?>

    @Upsert
    suspend fun upsert(p: WatchProgress)

    @Query("DELETE FROM progress WHERE profileId = :profileId AND `key` = :key")
    suspend fun delete(profileId: Long, key: String)

    @Query("DELETE FROM progress WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<UserLink>>

    @Query("SELECT * FROM links ORDER BY addedAt DESC")
    suspend fun all(): List<UserLink>

    @Insert
    suspend fun insert(l: UserLink): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [Profile::class, SourceConfig::class, LibraryEntry::class, WatchProgress::class, UserLink::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun sources(): SourceDao
    abstract fun library(): LibraryDao
    abstract fun progress(): ProgressDao
    abstract fun links(): LinkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cinehub.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
