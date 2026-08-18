package com.wardlog.app

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DictionaryDao {
    @Insert
    suspend fun insert(entry: DictionaryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Update
    suspend fun update(entry: DictionaryEntry)

    @Delete
    suspend fun delete(entry: DictionaryEntry)

    @Query("SELECT * FROM dictionary_entries ORDER BY category, canonical COLLATE NOCASE")
    fun getAll(): LiveData<List<DictionaryEntry>>

    @Query("SELECT * FROM dictionary_entries")
    suspend fun getAllSync(): List<DictionaryEntry>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun count(): Int
}
