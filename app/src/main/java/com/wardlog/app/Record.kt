package com.wardlog.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedNumber: String,
    val patientName: String,
    val consultant: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
