package com.phonapps.qrscanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observeById(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanEntity?

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    fun observeAllDesc(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY timestamp ASC")
    fun observeAllAsc(): Flow<List<ScanEntity>>

    @Query("SELECT COUNT(*) FROM scans")
    fun observeCount(): Flow<Int>

    @Query("UPDATE scans SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()

    /**
     * Finds a recent identical scan so rapid re-scans of the same code are not
     * stored as duplicates. [sinceEpochMs] bounds the look-back window.
     */
    @Query(
        "SELECT * FROM scans WHERE raw_value = :raw AND source = :source " +
            "AND timestamp >= :sinceEpochMs ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun findRecentDuplicate(raw: String, source: String, sinceEpochMs: Long): ScanEntity?
}
