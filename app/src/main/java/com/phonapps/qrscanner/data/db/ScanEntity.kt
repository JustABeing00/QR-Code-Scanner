package com.phonapps.qrscanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "raw_value") val rawValue: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "favorite") val favorite: Boolean = false,
    @ColumnInfo(name = "source") val source: String = ScanSource.CAMERA.name
)

enum class ScanSource { CAMERA, IMAGE, CLIPBOARD, SHARED }
