package com.rasel.rasgram.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rasel.rasgram.User
import com.rasel.rasgram.Message
import com.rasel.rasgram.Status
import com.rasel.rasgram.Group

@Database(entities = [User::class, Message::class, Status::class, Group::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun statusDao(): StatusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rasgram_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
