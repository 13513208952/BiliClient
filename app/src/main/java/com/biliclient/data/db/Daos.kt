package com.biliclient.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM account LIMIT 1")
    fun observe(): Flow<AccountEntity?>

    @Query("SELECT * FROM account LIMIT 1")
    suspend fun get(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: AccountEntity)

    @Query("DELETE FROM account")
    suspend fun clear()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE status IN (0,1,2,3)")
    suspend fun unfinished(): List<DownloadEntity>

    @Query("SELECT * FROM download WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(e: DownloadEntity): Long

    @Update
    suspend fun update(e: DownloadEntity)

    @Query("UPDATE download SET downloadedBytes=:b, status=:s, updatedAt=:t WHERE id=:id")
    suspend fun progress(id: Long, b: Long, s: Int, t: Long)

    @Delete
    suspend fun delete(e: DownloadEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY ts DESC LIMIT 20")
    fun recent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
