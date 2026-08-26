package com.samsunggalaxy.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for EPIC-08 T08.3 (doc/task/done/EPIC-08-engagement-features.md): a
 * real device/upgrade path for MIGRATION_2_3, which BodyMeasurementDaoTest's
 * Room.inMemoryDatabaseBuilder never exercises (in-memory DBs are always created fresh from
 * the current entities, skipping migrations entirely).
 *
 * Audit finding: MIGRATION_2_3's raw SQL declared `profileId INTEGER NOT NULL DEFAULT 0`, but
 * the BodyMeasurement entity has no matching @ColumnInfo(defaultValue). Room validates the
 * post-migration table against the entity-derived expected schema and throws
 * IllegalStateException("Migration didn't properly handle...") on mismatch — crashing every
 * upgrading user's app on startup. Fixed by dropping the DEFAULT clause from the migration SQL.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To4_roomOpensWithoutSchemaMismatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration_test_${System.nanoTime()}"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        // Hand-build a version-1 database (the schema before either migration), matching
        // AppDatabase's original entities.
        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1.execSQL(
            """
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isCurrent INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v1.execSQL(
            """
            CREATE TABLE bmi_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                height REAL NOT NULL,
                weight REAL NOT NULL,
                gender INTEGER NOT NULL,
                age INTEGER NOT NULL,
                bmi REAL NOT NULL,
                bmr REAL NOT NULL,
                tdee REAL NOT NULL,
                idealWeightMin REAL NOT NULL,
                idealWeightMax REAL NOT NULL,
                bodyFatPercentage REAL,
                profileId INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v1.version = 1
        v1.close()

        // Opening via Room with the real migration chain must succeed all the way to version 4
        // — this is exactly where the reported bug threw at db-open time.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            db.openHelper.writableDatabase // forces Room to actually open + validate schema
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    /**
     * EPIC-09 T09.2 — MIGRATION_3_4 adds `source`/`healthConnectRecordId` to bmi_records via
     * raw ALTER TABLE. Verifies the migration path in isolation (v3 schema, no goalWeight/
     * body_measurements churn) and that pre-existing rows get backfilled with source='APP'
     * by the column DEFAULT, matching what BmiRecord's Kotlin default expects.
     */
    @Test
    fun migrate3To4_addsSourceAndHealthConnectColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration_test_3_4_${System.nanoTime()}"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.delete()

        val v3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3.execSQL(
            """
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isCurrent INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                goalWeight REAL
            )
            """.trimIndent()
        )
        v3.execSQL(
            """
            CREATE TABLE bmi_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                height REAL NOT NULL,
                weight REAL NOT NULL,
                gender INTEGER NOT NULL,
                age INTEGER NOT NULL,
                bmi REAL NOT NULL,
                bmr REAL NOT NULL,
                tdee REAL NOT NULL,
                idealWeightMin REAL NOT NULL,
                idealWeightMax REAL NOT NULL,
                bodyFatPercentage REAL,
                profileId INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v3.execSQL(
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
        v3.execSQL(
            "INSERT INTO bmi_records (timestamp, height, weight, gender, age, bmi, bmr, tdee, idealWeightMin, idealWeightMax, bodyFatPercentage, profileId) VALUES (1000, 170.0, 70.0, 0, 30, 24.2, 1600.0, 2200.0, 60.0, 80.0, NULL, 1)"
        )
        v3.version = 3
        v3.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()
        try {
            val cursor = db.openHelper.writableDatabase.query("SELECT source, healthConnectRecordId FROM bmi_records")
            try {
                assert(cursor.moveToFirst()) { "expected the pre-existing row to survive the migration" }
                val sourceIndex = cursor.getColumnIndexOrThrow("source")
                val hcIdIndex = cursor.getColumnIndexOrThrow("healthConnectRecordId")
                assert(cursor.getString(sourceIndex) == "APP") { "pre-existing rows must backfill source='APP'" }
                assert(cursor.isNull(hcIdIndex)) { "pre-existing rows must have a null healthConnectRecordId" }
            } finally {
                cursor.close()
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
