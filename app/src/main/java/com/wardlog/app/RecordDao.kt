package com.wardlog.app

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<Record>>

    @Query(
        """
        SELECT * FROM records 
        WHERE bedNumber LIKE '%' || :query || '%' 
           OR patientName LIKE '%' || :query || '%' 
           OR consultant LIKE '%' || :query || '%' 
           OR details LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        """
    )
    fun search(query: String): LiveData<List<Record>>
}
