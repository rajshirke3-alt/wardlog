package com.wardlog.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Record::class, DictionaryEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wardlog.db"
                )
                    // No one has real WardLog data on device 1 -> 2 yet (this
                    // project hasn't shipped), so a destructive fallback is
                    // fine here and keeps setup simple. If you're updating an
                    // installed app, uninstall/reinstall once after this
                    // change so the new dictionary table gets created.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Populates the dictionary table with built-in defaults on first run only. */
        suspend fun seedDictionaryIfEmpty(dao: DictionaryDao) {
            if (dao.count() > 0) return
            val seed = mutableListOf<DictionaryEntry>()
            MedicalTermCorrector.DEFAULT_ALIASES.forEach { (alias, canonical) ->
                seed.add(DictionaryEntry(category = DictionaryEntry.CATEGORY_TERM, alias = alias, canonical = canonical))
            }
            MedicalTermCorrector.DEFAULT_DOCTORS.forEach { name ->
                seed.add(DictionaryEntry(category = DictionaryEntry.CATEGORY_DOCTOR, alias = name, canonical = name))
            }
            dao.insertAll(seed)
        }
    }
}
