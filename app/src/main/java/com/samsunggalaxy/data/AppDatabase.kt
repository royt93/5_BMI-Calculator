package com.samsunggalaxy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BmiRecord::class, Profile::class, BodyMeasurement::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bmiDao(): BmiDao
    abstract fun profileDao(): ProfileDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration: add goalWeight column to profiles table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN goalWeight REAL")
            }
        }

        // EPIC-08 T08.3 — new table for waist/neck/hip/chest tracked independently of a weigh-in.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `body_measurements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `waist` REAL,
                        `neck` REAL,
                        `hip` REAL,
                        `chest` REAL,
                        `profileId` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // EPIC-09 T09.2 — Health Connect sync linkage. `source` defaults to 'APP' so every
        // pre-existing row (all created in-app before this migration) is correctly tagged
        // without a backfill pass.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bmi_records ADD COLUMN source TEXT NOT NULL DEFAULT 'APP'")
                db.execSQL("ALTER TABLE bmi_records ADD COLUMN healthConnectRecordId TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bmi_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
