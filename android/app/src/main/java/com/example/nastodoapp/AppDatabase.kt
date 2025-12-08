package com.example.nastodoapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Task::class, OfflineNote::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nastodo_database"
                )
                    // 开发阶段允许主线程查询 (生产环境建议去掉，但为了方便迁移先留着)
                    // .allowMainThreadQueries()
                    .fallbackToDestructiveMigration() // 版本升级时如果冲突直接重建库
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}