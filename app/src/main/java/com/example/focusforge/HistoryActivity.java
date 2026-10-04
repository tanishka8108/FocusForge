package com.example.focusforge;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat; // formats dates into "Mon", "Tue", etc
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

// The "Forest Garden" screen: summary stats, weekly chart, full session history
public class HistoryActivity extends AppCompatActivity {

    private TextView tvTotalFocusMinutes, tvSuccessRate, tvTreesSaved;
    private ListView lvHistory; // scrollable list of past sessions
    private Button btnBack;
    private DatabaseHelper databaseHelper;
    private WeeklyBarChartView weeklyChartView; // custom-drawn bar chart

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // required first call
        setContentView(R.layout.activity_history); // inflate this screen's XML

        if (getSupportActionBar() != null) { // defensive null-check
            getSupportActionBar().hide(); // hides Android's default title bar
        }

        databaseHelper = new DatabaseHelper(this);

        tvTotalFocusMinutes = findViewById(R.id.tvTotalFocusMinutes); // connect fields to XML views
        tvSuccessRate = findViewById(R.id.tvSuccessRate);
        tvTreesSaved = findViewById(R.id.tvTreesSaved);
        lvHistory = findViewById(R.id.lvHistory);
        btnBack = findViewById(R.id.btnBack);
        weeklyChartView = findViewById(R.id.weeklyChartView);

        btnBack.setOnClickListener(v -> finish()); // finish() closes this Activity, returns to MainActivity

        loadGardenData(); // populate stats + session list
        loadWeeklyChart(); // populate the bar chart
    }

    private void loadGardenData() { // reads sessions, computes stats, fills the ListView
        ArrayList<ForestSession> sessionList = new ArrayList<>(); // one ForestSession per DB row
        Cursor cursor = databaseHelper.getAllSessions();

        int totalMinutes = 0; // running totals built up while looping
        int successfulCount = 0;
        int totalSessions = 0;

        if (cursor != null) { // defensive null-check
            int statusIndex = cursor.getColumnIndex(DatabaseHelper.COL_STATUS); // look up column indexes once
            int durationIndex = cursor.getColumnIndex(DatabaseHelper.COL_DURATION);
            int xpIndex = cursor.getColumnIndex(DatabaseHelper.COL_XP);

            while (cursor.moveToNext()) { // step through every row
                String status = cursor.getString(statusIndex);
                int duration = cursor.getInt(durationIndex);
                int xp = cursor.getInt(xpIndex);

                totalSessions++; // count every session, success or fail
                if (status != null && status.toLowerCase().contains("success")) {
                    successfulCount++; // only successes count toward these totals
                    totalMinutes += duration;
                }

                sessionList.add(new ForestSession(status, duration, xp)); // wrap row into an object
            }
            cursor.close(); // always close when finished
        }

        ForestAdapter adapter = new ForestAdapter(this, sessionList); // build adapter with the populated list
        lvHistory.setAdapter(adapter); // attach it so it renders

        tvTotalFocusMinutes.setText(String.format(Locale.getDefault(), "%dm", totalMinutes));
        tvTreesSaved.setText(String.valueOf(successfulCount));

        int rate = totalSessions > 0 ? (successfulCount * 100) / totalSessions : 0; // avoid divide-by-zero on fresh install
        tvSuccessRate.setText(String.format(Locale.getDefault(), "%d%%", rate));
    }

    private void loadWeeklyChart() { // feeds the last 7 days of data into the custom chart view
        if (weeklyChartView == null) return; // safety check

        int[] minutesByDay = databaseHelper.getWeeklyFocusMinutes(); // 7-day minute totals

        String[] labels = new String[7]; // will hold "Mon", "Tue", etc
        Calendar cal = Calendar.getInstance(); // start from right now
        cal.add(Calendar.DAY_OF_YEAR, -6); // step back 6 days, matching the DB query's range
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.getDefault()); // "EEE" = abbreviated day name
        for (int i = 0; i < 7; i++) { // once per day in range
            labels[i] = sdf.format(cal.getTime()); // format this day as a short label
            cal.add(Calendar.DAY_OF_YEAR, 1); // advance one day for next loop
        }

        weeklyChartView.setData(minutesByDay, labels); // hand both arrays to the chart
    }
}