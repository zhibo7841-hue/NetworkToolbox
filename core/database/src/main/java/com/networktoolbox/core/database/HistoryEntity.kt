package com.networktoolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.networktoolbox.core.common.history.HistoryRecord
import com.networktoolbox.core.common.history.HistoryType

@Entity(
    tableName = "history_records",
    indices = [Index(value = ["timestamp"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val type: String,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "detail_json")
    val detailJson: String,
)

fun HistoryRecord.toEntity(): HistoryEntity = HistoryEntity(
    id = id,
    timestamp = timestamp,
    type = type.name,
    title = title,
    summary = summary,
    detailJson = detailJson,
)

fun HistoryEntity.toRecord(): HistoryRecord = HistoryRecord(
    id = id,
    timestamp = timestamp,
    type = type.toHistoryType(),
    title = title,
    summary = summary,
    detailJson = detailJson,
)

private fun String.toHistoryType(): HistoryType =
    runCatching { HistoryType.valueOf(this) }.getOrDefault(HistoryType.UNKNOWN)
