package com.example.focusforge;

import android.content.ContentValues; // Key-value container used when inserting/updating DB rows
import android.content.Context; // Needed by SQLiteOpenHelper to locate app storage
import android.database.Cursor; // Represents the result rows of a SQL query
import android.database.sqlite.SQLiteDatabase; // The actual database connection object
import android.database.sqlite.SQLiteOpenHelper;  // Base class that manages creating/upgrading the DB file

import java.util.Calendar;                                     // Used to calculate "the last 7 days" date range

// This class wraps all direct SQLite access in one place, so the rest of the
// app never has to write raw SQL itself - it just calls friendly methods like
// insertSession() or getAllSessions().
public class DatabaseHelper extends SQLiteOpenHelper {          // Inherits Android's standard DB-management base class

    private static final String DATABASE_NAME = "FocusForge.db"; // The actual filename stored on the device
    private static final int DATABASE_VERSION = 4;               // Bumped each time the table structure (schema) changes

    // Table and column names as constants - avoids typos like "duratoin"
    // breaking a query somewhere deep in the code.
    public static final String TABLE_NAME = "sessions";
    public static final String COL_ID = "id";
    public static final String COL_STATUS = "status";
    public static final String COL_DURATION = "duration";
    public static final String COL_XP = "xp";
    public static final String COL_TIMESTAMP = "timestamp";

    // Constructor: passes the DB name/version up to the Android base class,
    // which handles creating the file and calling onCreate()/onUpgrade() as needed.
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);  // null = use the default Cursor factory
    }

    // Called automatically ONE TIME ONLY: the very first time the app runs
    // and no database file exists yet.
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " ("     // Build the SQL statement as a string
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "         // Auto-incrementing unique row ID
                + COL_STATUS + " TEXT, "                                  // Session outcome, stored as text
                + COL_DURATION + " INTEGER, "                             // Minutes, stored as a whole number
                + COL_XP + " INTEGER, "                                   // XP earned, whole number
                + COL_TIMESTAMP + " INTEGER)";                            // Unix timestamp in milliseconds
        db.execSQL(createTableQuery);                                    // Actually run that SQL against the database
    }

    // Called automatically whenever DATABASE_VERSION goes UP from what's
    // currently stored on the device (e.g. we add a new column and bump 3 -> 4).
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple (destructive) migration strategy: wipe the old table and
        // recreate it fresh. Fine during active development; a production
        // app would instead use ALTER TABLE to preserve existing data.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);                // Delete the old table if it exists
        onCreate(db);                                                    // Recreate it using the current schema
    }

    // Inserts one new session row. Called every time a focus session ends
    // (whether successfully or by failing).
    public boolean insertSession(String status, int duration, int xp) {
        SQLiteDatabase db = this.getWritableDatabase();                  // Open the DB in write mode
        ContentValues values = new ContentValues();                     // Build a key->value map of the new row's data
        values.put(COL_STATUS, status);
        values.put(COL_DURATION, duration);
        values.put(COL_XP, xp);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());          // Stamp this row with "right now"

        long result = db.insert(TABLE_NAME, null, values);              // Insert; returns the new row's ID, or -1 on failure
        return result != -1;                                            // Convert that result into a simple true/false
    }

    // Fetches every logged session, newest first - used by the Forest Garden
    // history list.
    public Cursor getAllSessions() {
        SQLiteDatabase db = this.getReadableDatabase();                 // Open the DB in read-only mode
        return db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY " + COL_ID + " DESC", null);
        // "ORDER BY id DESC" means highest (most recent) ID first
    }

    /**
     * Returns successful-session minutes bucketed into the last 7 days (index 0 = 6 days ago,
     * index 6 = today), using local midnight as each day's boundary.
     */
    public int[] getWeeklyFocusMinutes() {
        int[] minutesByDay = new int[7];                                // One slot per day, all start at 0
        SQLiteDatabase db = this.getReadableDatabase();

        Calendar cal = Calendar.getInstance();                          // Get a Calendar set to "right now"
        cal.set(Calendar.HOUR_OF_DAY, 0);                               // Zero out the time-of-day fields...
        cal.set(Calendar.MINUTE, 0);                                    // ...so we land exactly on midnight...
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -6);                              // ...then step back 6 days from today
        long startOfRange = cal.getTimeInMillis();                      // Convert to a raw millisecond timestamp

        // Query only rows whose timestamp falls within our 7-day window.
        // The "?" is a placeholder, safely filled in by the array below -
        // this protects against SQL injection compared to string-concatenating values directly.
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_TIMESTAMP + ", " + COL_DURATION + ", " + COL_STATUS +
                        " FROM " + TABLE_NAME + " WHERE " + COL_TIMESTAMP + " >= ?",
                new String[]{String.valueOf(startOfRange)}
        );

        if (cursor != null) {                                           // Defensive check - query could return null
            int tsIdx = cursor.getColumnIndex(COL_TIMESTAMP);           // Look up which column index holds each field
            int durIdx = cursor.getColumnIndex(COL_DURATION);
            int statusIdx = cursor.getColumnIndex(COL_STATUS);

            while (cursor.moveToNext()) {                               // Loop through every matching row
                long ts = cursor.getLong(tsIdx);                        // Read that row's timestamp
                int duration = cursor.getInt(durIdx);                   // Read that row's duration
                String status = cursor.getString(statusIdx);            // Read that row's status text

                if (status == null || !status.toLowerCase().contains("success")) continue;
                // Skip failed sessions entirely - only successes count toward the chart

                int dayOffset = (int) ((ts - startOfRange) / (24L * 60 * 60 * 1000));
                // Work out how many full days after startOfRange this session happened
                // (24L * 60 * 60 * 1000 = milliseconds in one day; the L makes it a long)

                if (dayOffset >= 0 && dayOffset < 7) {                  // Safety check - stay inside our 7 slots
                    minutesByDay[dayOffset] += duration;                // Add this session's minutes into the right day
                }
            }
            cursor.close();                                             // Always close a Cursor when done - frees resources
        }

        return minutesByDay;                                            // Hand the finished 7-day array back to the caller
    }
}