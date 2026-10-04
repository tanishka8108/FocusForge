package com.example.focusforge;

import android.content.Context;                                 // Represents the app's running environment
import android.content.Intent;                                   // Used to start other Activities/Services and pass data
import android.content.SharedPreferences;                        // Simple persistent key-value storage
import android.os.Bundle;                                        // Carries saved instance state into onCreate()
import android.os.CountDownTimer;                                 // Built-in ticking countdown timer class
import android.os.Vibrator;                                       // Controls the device's vibration motor
import android.speech.tts.TextToSpeech;                           // Converts text into spoken audio
import android.util.Log;                                          // Writes diagnostic messages to Logcat
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;                                      // Small temporary popup message

import androidx.appcompat.app.AlertDialog;                        // Standard pop-up dialog with buttons/options
import androidx.appcompat.app.AppCompatActivity;                  // Base class for this screen
import androidx.core.content.ContextCompat;                       // Safe way to read color resources

import com.google.android.material.bottomsheet.BottomSheetDialog; // Slide-up panel used for the AI chat UI

import org.json.JSONArray;                                        // Represents a JSON array we build/parse
import org.json.JSONObject;                                       // Represents a JSON object we build/parse

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;                                          // Ensures consistent number/date formatting

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;                                      // The HTTP client used to call the Groq AI API
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// "implements TextToSpeech.OnInitListener" means this class promises to
// provide an onInit() method, which TextToSpeech calls once it's ready.
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String PREFS_NAME = "FocusForgePrefs";   // Filename used for SharedPreferences storage

    // --- View references, connected in initViews() ---
    private TextView tvStats, tvStreak;
    private TextView tvBossIcon, tvBossName, tvBossDefeated, tvBossHp;
    private ProgressBar bossHpBar;

    private ProgressBar timerProgressBar;
    private TextView tvTreeEmoji, tvTimer, tvTreeStatus;

    private RadioGroup rgSubjects, rgSound;
    private RadioButton rbLofi, rbRain, rbWhiteNoise, rbMute;
    private RadioButton rbSubjectCoding, rbSubjectMath, rbSubjectDeepWork;
    private String selectedSubject = "Coding";                    // Default subject before user picks one

    private Button btnStart, btnReset, btnSetTime;
    private Button btnViewHistory, btnStudyBot;

    // --- Timer state ---
    private CountDownTimer countDownTimer;                        // The active timer object (null when not running)
    private long totalTimeInMillis = 25 * 60 * 1000;              // Default session length: 25 minutes, in milliseconds
    private long timeLeftInMillis = totalTimeInMillis;             // How much time remains right now
    private boolean isTimerRunning = false;                        // Flag: is a session currently active?
    private boolean isInternalNavigation = false;                  // Flag: are we leaving MainActivity on PURPOSE (not a distraction)?

    // --- Gamification data (parallel arrays for boss info) ---
    private final String[] BOSS_NAMES = {"Procrastination Demon", "Distraction Hydra", "Sloth Titan"};
    private final String[] BOSS_ICONS = {"👹", "🐉", "🗿"};
    private final int[] BOSS_MAX_HPS = {200, 400, 800};
    private int currentBossIndex = 0;                              // Which boss (array index) is currently active
    private int bossCurrentHp = 200;                                // Current HP of the active boss
    private int bossesDefeated = 0;                                 // Lifetime count of bosses defeated
    private int userXp = 0;                                         // Lifetime XP earned
    private int treesCount = 0;                                     // Lifetime successful sessions / trees grown

    // --- Supporting services/helpers ---
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;                             // Guards against speaking before TTS finishes initializing
    private Vibrator vibrator;
    private DatabaseHelper databaseHelper;

    private final OkHttpClient httpClient = new OkHttpClient();    // Reused HTTP client for all Groq API calls
    private final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;  // Injected at build-time from local.properties

    // Called ONCE when this screen is first created.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);                        // Required first call - lets AppCompatActivity set up
        setContentView(R.layout.activity_main);                    // Inflate this screen's XML layout

        textToSpeech = new TextToSpeech(getApplicationContext(), this); // "this" = MainActivity receives the ready-callback
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); // Ask Android for the vibration controller
        databaseHelper = new DatabaseHelper(this);                 // Create our SQLite wrapper

        // Order matters here: connect views, wire listeners, THEN load saved
        // data, THEN refresh the UI to reflect it.
        initViews();
        setupListeners();
        loadPlayerState();
        updateStreakDisplay();
        updateTopStats();
    }

    // Connects every Java field above to its actual on-screen XML view.
    private void initViews() {
        tvStats = findViewById(R.id.tvStats);
        tvStreak = findViewById(R.id.tvStreak);

        tvBossIcon = findViewById(R.id.tvBossIcon);
        tvBossName = findViewById(R.id.tvBossName);
        tvBossDefeated = findViewById(R.id.tvBossDefeated);
        tvBossHp = findViewById(R.id.tvBossHp);
        bossHpBar = findViewById(R.id.bossHpBar);

        timerProgressBar = findViewById(R.id.timerProgressBar);
        tvTreeEmoji = findViewById(R.id.tvTreeEmoji);
        tvTimer = findViewById(R.id.tvTimer);
        tvTreeStatus = findViewById(R.id.tvTreeStatus);

        rgSubjects = findViewById(R.id.rgSubjects);
        rgSound = findViewById(R.id.rgSound);
        rbLofi = findViewById(R.id.rbLofi);
        rbRain = findViewById(R.id.rbRain);
        rbWhiteNoise = findViewById(R.id.rbWhiteNoise);
        rbMute = findViewById(R.id.rbMute);

        rbSubjectCoding = findViewById(R.id.rbSubjectCoding);
        rbSubjectMath = findViewById(R.id.rbSubjectMath);
        rbSubjectDeepWork = findViewById(R.id.rbSubjectDeepWork);

        btnStart = findViewById(R.id.btnStart);
        btnReset = findViewById(R.id.btnReset);
        btnSetTime = findViewById(R.id.btnSetTime);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnStudyBot = findViewById(R.id.btnStudyBot);

        updateTimerDisplay();                                      // Show the initial "25:00" text
        updateSubjectPillStyles(R.id.rbSubjectCoding);              // Manually apply the default-selected pill styling
        updateSoundPillStyles(R.id.rbLofi);                         // (listeners only fire on USER interaction, not initial state)
    }

    // Attaches click/selection listeners to every interactive view.
    private void setupListeners() {
        if (rgSubjects != null) {                                  // Defensive null-check before using the view
            // Lambda: runs whenever the checked RadioButton inside rgSubjects changes
            rgSubjects.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.rbSubjectCoding) {
                    selectedSubject = "Coding";
                } else if (checkedId == R.id.rbSubjectMath) {
                    selectedSubject = "Math";
                } else {
                    selectedSubject = "Deep Work";
                }
                updateSubjectPillStyles(checkedId);                 // Re-style so the tapped pill highlights
            });
        }

        if (rgSound != null) {
            rgSound.setOnCheckedChangeListener((group, checkedId) -> {
                updateSoundPillStyles(checkedId);

                // If a session is already running, switch the ambient track
                // live instead of waiting for the next session to start.
                if (isTimerRunning) {
                    String track = getSelectedAudioTrack();
                    if (track != null) {
                        startAudioService(track);
                    } else {
                        stopAudioService();
                    }
                }
            });
        }

        btnStart.setOnClickListener(v -> {
            if (!isTimerRunning) {                                  // Guard against double-tap starting two timers
                startFocusSession();
            }
        });

        btnReset.setOnClickListener(v -> {
            if (isTimerRunning) {                                   // Only allow "Give Up" while a session is active
                failSession("Session surrendered.");
            }
        });

        btnSetTime.setOnClickListener(v -> showPresetsDialog());

        if (btnViewHistory != null) {
            btnViewHistory.setOnClickListener(v -> {
                isInternalNavigation = true;                        // Mark this as intentional so onUserLeaveHint() doesn't fail the session
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);                               // Launch HistoryActivity
            });
        }

        if (btnStudyBot != null) {
            btnStudyBot.setOnClickListener(v -> {
                isInternalNavigation = true;                         // Same reasoning as above
                showStudyBotSheet();
            });
        }
    }

    // Applies "active" vs "inactive" visual styling across all 3 subject pills.
    private void updateSubjectPillStyles(int checkedId) {
        RadioButton[] buttons = {rbSubjectCoding, rbSubjectMath, rbSubjectDeepWork};
        for (RadioButton rb : buttons) {                            // Loop through all three
            if (rb == null) continue;                                // Skip if a reference somehow wasn't found
            if (rb.getId() == checkedId) {                           // This is the one the user just selected
                rb.setBackgroundResource(R.drawable.bg_pill_active);
                rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else {                                                  // Every other pill goes back to "inactive" styling
                rb.setBackgroundResource(R.drawable.bg_card_glass);
                rb.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }

    // Same idea, but for the 4 ambient-sound pills.
    private void updateSoundPillStyles(int checkedId) {
        RadioButton[] buttons = {rbLofi, rbRain, rbWhiteNoise, rbMute};
        for (RadioButton rb : buttons) {
            if (rb == null) continue;
            if (rb.getId() == checkedId) {
                rb.setBackgroundResource(R.drawable.bg_pill_active);
                rb.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else {
                rb.setBackgroundResource(R.drawable.bg_card_glass);
                rb.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }

    /** Returns the AudioService track key for whichever sound pill is selected, or null for Mute. */
    private String getSelectedAudioTrack() {
        if (rbLofi != null && rbLofi.isChecked()) return AudioService.TRACK_LOFI;
        if (rbRain != null && rbRain.isChecked()) return AudioService.TRACK_RAIN;
        if (rbWhiteNoise != null && rbWhiteNoise.isChecked()) return AudioService.TRACK_WHITE_NOISE;
        return null;                                                 // Mute is selected (or nothing matched)
    }

    // Begins a new focus session: disables controls, starts audio, starts the countdown.
    private void startFocusSession() {
        isTimerRunning = true;
        btnStart.setEnabled(false);                                  // Prevent starting a second overlapping session
        btnSetTime.setEnabled(false);                                // Prevent changing duration mid-session

        String track = getSelectedAudioTrack();
        if (track != null) {
            startAudioService(track);
        }

        speakText("Focus session started for " + selectedSubject);

        // Anonymous inner class: creates an unnamed CountDownTimer subclass
        // right here, overriding its two required methods on the spot.
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) { // Count down from timeLeftInMillis, ticking every 1000ms
            @Override
            public void onTick(long millisUntilFinished) {          // Fires once per second while counting down
                timeLeftInMillis = millisUntilFinished;
                updateTimerDisplay();
                updateTreeGrowthStage();
            }

            @Override
            public void onFinish() {                                 // Fires exactly once, when the countdown hits zero
                completeFocusSession();
            }
        }.start();                                                    // Actually starts the timer running
    }

    // Handles all the "session succeeded" reward logic.
    private void completeFocusSession() {
        isTimerRunning = false;
        btnStart.setEnabled(true);                                   // Re-enable controls for the next session
        btnSetTime.setEnabled(true);
        stopAudioService();

        int sessionMinutes = (int) (totalTimeInMillis / 60000);      // Convert milliseconds -> minutes (60,000ms = 1 minute)
        int xpEarned = Math.max(10, sessionMinutes * 2);              // 2 XP per minute, minimum 10
        int bossDamage = Math.max(20, sessionMinutes * 10);           // 10 damage per minute, minimum 20

        userXp += xpEarned;                                            // Shorthand for userXp = userXp + xpEarned
        treesCount++;                                                  // Increment by exactly 1
        bossCurrentHp = Math.max(0, bossCurrentHp - bossDamage);       // Clamp at 0 so HP never displays negative

        if (bossCurrentHp <= 0) {                                      // Boss defeated!
            bossesDefeated++;
            currentBossIndex = (currentBossIndex + 1) % BOSS_NAMES.length; // Wrap back to index 0 after the last boss
            bossCurrentHp = BOSS_MAX_HPS[currentBossIndex];             // Reset HP to the new boss's max
            speakText("Boss defeated! The " + BOSS_NAMES[currentBossIndex] + " has appeared.");
        } else {
            speakText("Focus session complete. Tree successfully grown!");
        }

        try {                                                          // Wrapped in try/catch - a DB write failure shouldn't crash the app
            databaseHelper.insertSession("Success (" + selectedSubject + ")", Math.max(1, sessionMinutes), xpEarned);
        } catch (Exception e) {
            Log.e("DB_ERROR", "Failed to save session", e);
        }

        if (vibrator != null) {
            vibrator.vibrate(400);                                    // Vibrate for 400 milliseconds
        }

        tvTreeEmoji.setText("🌳");                                     // Show the fully-grown tree
        tvTreeStatus.setText("Stage 3: Full Canopy!");
        tvTreeStatus.setTextColor(ContextCompat.getColor(this, R.color.success)); // Green = success color
        updateTopStats();                                              // Refresh all the on-screen stat displays
        savePlayerState();                                             // CRITICAL: persist the new state so it survives app restart
        resetTimer();                                                  // Reset the countdown back to its starting duration
        Toast.makeText(this, "Session Saved to Forest!", Toast.LENGTH_SHORT).show(); // Brief confirmation popup
    }

    // Handles all the "session failed" logic (given up, or distracted).
    private void failSession(String reason) {
        if (countDownTimer != null) {
            countDownTimer.cancel();                                  // Stop the ticking timer immediately
        }
        isTimerRunning = false;
        btnStart.setEnabled(true);
        btnSetTime.setEnabled(true);
        stopAudioService();

        speakText("Distraction detected! Tree withered.");

        if (vibrator != null) {
            vibrator.vibrate(300);
        }

        tvTreeEmoji.setText("🥀");                                     // Wilted flower = failed session
        tvTreeStatus.setText("Withered Tree! Session Failed.");
        tvTreeStatus.setTextColor(ContextCompat.getColor(this, R.color.danger)); // Red = failure color

        try {
            databaseHelper.insertSession("Failed (" + selectedSubject + ")", 0, 0); // 0 duration/XP for a failed session
        } catch (Exception e) {
            Log.e("DB_ERROR", "Failed to save failed session", e);
        }

        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
        resetTimer();
    }

    // Android calls this specifically when the user navigates AWAY from the
    // app on purpose (Home button, app switcher) - not for things like the
    // screen turning off or an incoming call.
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isTimerRunning && !isInternalNavigation) {                // Only fail if a session is active AND this wasn't an internal nav
            failSession("Distraction detected! Tree withered.");
        }
    }

    // Called every time this screen comes back into the foreground -
    // including returning from HistoryActivity or closing the study bot sheet.
    @Override
    protected void onResume() {
        super.onResume();
        isInternalNavigation = false;                                 // Reset the flag so the NEXT leave-hint is evaluated fresh
    }

    // Called when this screen goes into the background - a good safety-net
    // moment to save state, in case the app gets killed before a session
    // naturally completes or fails.
    @Override
    protected void onPause() {
        super.onPause();
        savePlayerState();
    }

    // Updates the big "MM:SS" timer text and the circular progress ring.
    private void updateTimerDisplay() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;           // Total seconds remaining, divided into minutes
        int seconds = (int) (timeLeftInMillis / 1000) % 60;           // ...and the leftover seconds
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)); // %02d = zero-padded 2-digit number

        if (timerProgressBar != null && totalTimeInMillis > 0) {
            int progress = (int) (((double) timeLeftInMillis / totalTimeInMillis) * 1500); // Scale remaining time onto a 0-1500 range
            timerProgressBar.setProgress(progress);                   // 1500 matches the ProgressBar's max, set in the XML
        }
    }

    // Updates the tree emoji/status text based on how far through the session we are.
    private void updateTreeGrowthStage() {
        double elapsedRatio = (double) (totalTimeInMillis - timeLeftInMillis) / totalTimeInMillis; // 0.0 = just started, 1.0 = finished
        if (elapsedRatio < 0.33) {                                     // First third of the session
            tvTreeEmoji.setText("🌱");
            tvTreeStatus.setText("Stage 1: Planted Sprout");
        } else if (elapsedRatio < 0.66) {                              // Middle third
            tvTreeEmoji.setText("🌿");
            tvTreeStatus.setText("Stage 2: Growing Sapling");
        } else {                                                        // Final third
            tvTreeEmoji.setText("🌲");
            tvTreeStatus.setText("Stage 3: Flourishing Tree");
        }
    }

    // Refreshes every stat display (XP, level, boss info) from current field values.
    private void updateTopStats() {
        int level = (userXp / 50) + 1;                                 // Simple leveling formula: every 50 XP = 1 level, starting at 1
        tvStats.setText(String.format(Locale.getDefault(), "Lvl %d • %d XP • 🌲 %d Trees", level, userXp, treesCount));

        tvBossIcon.setText(BOSS_ICONS[currentBossIndex]);
        tvBossName.setText(BOSS_NAMES[currentBossIndex]);
        tvBossDefeated.setText(String.format(Locale.getDefault(), "Slain: %d", bossesDefeated));
        tvBossHp.setText(String.format(Locale.getDefault(), "HP: %d/%d", bossCurrentHp, BOSS_MAX_HPS[currentBossIndex]));

        bossHpBar.setMax(BOSS_MAX_HPS[currentBossIndex]);              // The HP bar's max changes per-boss (200/400/800)
        bossHpBar.setProgress(bossCurrentHp);
    }

    // Calculates and displays the daily study streak, using SharedPreferences
    // to remember the last day studied.
    private void updateStreakDisplay() {
        if (tvStreak == null) return;                                  // Safety check
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastTime = prefs.getLong("last_study_day", 0);            // 0 = never studied before (default)
        int currentStreak = prefs.getInt("study_streak", 1);           // 1 = default starting streak

        long now = System.currentTimeMillis();
        long daysDiff = (now - lastTime) / (1000 * 60 * 60 * 24);      // How many full days since the last recorded study day

        if (daysDiff == 1) {                                           // Studied yesterday, and again today - streak continues
            currentStreak++;
            prefs.edit().putInt("study_streak", currentStreak).putLong("last_study_day", now).apply();
        } else if (daysDiff > 1) {                                     // Missed one or more days - streak resets
            currentStreak = 1;
            prefs.edit().putInt("study_streak", 1).putLong("last_study_day", now).apply();
        }
        // (if daysDiff == 0, they already studied today - streak stays as-is, no update needed)

        tvStreak.setText(String.format(Locale.getDefault(), "🔥 %d Day Streak", currentStreak));
    }

    // Writes all gamification progress fields to SharedPreferences.
    private void savePlayerState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()                                                    // Start an editing transaction
                .putInt("user_xp", userXp)                              // Each putInt() call chains onto the last (method chaining)
                .putInt("trees_count", treesCount)
                .putInt("current_boss_index", currentBossIndex)
                .putInt("boss_current_hp", bossCurrentHp)
                .putInt("bosses_defeated", bossesDefeated)
                .apply();                                                // Commit all changes, asynchronously in the background
    }

    // Reads all gamification progress fields back from SharedPreferences.
    private void loadPlayerState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userXp = prefs.getInt("user_xp", 0);                            // Second argument = default value if key doesn't exist yet
        treesCount = prefs.getInt("trees_count", 0);
        currentBossIndex = prefs.getInt("current_boss_index", 0);
        bossesDefeated = prefs.getInt("bosses_defeated", 0);
        bossCurrentHp = prefs.getInt("boss_current_hp", BOSS_MAX_HPS[currentBossIndex]); // Default to full HP for a fresh install
    }

    // Resets the countdown back to its configured starting duration.
    private void resetTimer() {
        timeLeftInMillis = totalTimeInMillis;
        updateTimerDisplay();
    }

    // Shows the duration-picker dialog when PRESETS is tapped.
    private void showPresetsDialog() {
        String[] options = {"00:05 (Quick Demo)", "10 Minutes (Short)", "25 Minutes (Standard Pomodoro)", "50 Minutes (Deep Work)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);   // Builder pattern - configure step by step
        builder.setTitle("Select Focus Duration");
        builder.setItems(options, (dialog, which) -> {                  // "which" = index of the option the user tapped
            switch (which) {
                case 0: totalTimeInMillis = 5 * 1000; break;             // 5 seconds (demo only)
                case 1: totalTimeInMillis = 10 * 60 * 1000; break;       // 10 minutes
                case 2: totalTimeInMillis = 25 * 60 * 1000; break;       // 25 minutes
                case 3: totalTimeInMillis = 50 * 60 * 1000; break;       // 50 minutes
            }
            resetTimer();                                                // Apply the newly chosen duration immediately
        });
        builder.show();
    }

    // Builds and displays the AI Study Buddy bottom sheet dialog.
    private void showStudyBotSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_study_bot, null); // Manually inflate the dialog's XML layout
        dialog.setContentView(sheetView);

        dialog.setOnDismissListener(d -> isInternalNavigation = false); // Reset the flag once the sheet is closed

        ListView chatListView = sheetView.findViewById(R.id.chatListView);
        EditText etChatInput = sheetView.findViewById(R.id.etChatInput);
        Button btnSendChat = sheetView.findViewById(R.id.btnSendChat);

        ArrayList<ChatMessage> messageList = new ArrayList<>();         // Will hold the whole conversation
        ChatAdapter adapter = new ChatAdapter(this, messageList);
        chatListView.setAdapter(adapter);                                // Connect the adapter so the ListView can render it

        messageList.add(new ChatMessage("Hi! Ask any " + selectedSubject + " doubt so you don't leave your focus session.", false));
        adapter.notifyDataSetChanged();                                  // Tell the ListView "data changed, redraw yourself"

        btnSendChat.setOnClickListener(v -> {
            String prompt = etChatInput.getText().toString().trim();    // Grab and clean up the typed text
            if (prompt.isEmpty()) return;                                 // Ignore empty submissions

            messageList.add(new ChatMessage(prompt, true));              // true = this message is from the user
            adapter.notifyDataSetChanged();
            etChatInput.setText("");                                      // Clear the input field

            messageList.add(new ChatMessage("Thinking...", false));      // Temporary placeholder while waiting for the API
            adapter.notifyDataSetChanged();

            fetchGroqResponse(prompt, messageList, adapter);              // Kick off the actual network request
        });

        dialog.show();
    }

    // Sends the user's question to Groq's API and handles the response.
    private void fetchGroqResponse(String prompt, ArrayList<ChatMessage> list, ChatAdapter adapter) {
        try {
            JSONObject systemMessage = new JSONObject();                 // Defines the AI's behavior/persona
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are an academic study assistant in a focus app. Answer concisely in under 3 sentences.");

            JSONObject userMessage = new JSONObject();                   // The actual question being asked
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            JSONArray messagesArray = new JSONArray();                   // Groq expects an ordered array of messages
            messagesArray.put(systemMessage);
            messagesArray.put(userMessage);

            JSONObject requestBodyJson = new JSONObject();               // The full request payload
            requestBodyJson.put("model", "openai/gpt-oss-120b");         // Which AI model to use
            requestBodyJson.put("messages", messagesArray);
            requestBodyJson.put("max_tokens", 150);                      // Caps how long the reply can be

            RequestBody body = RequestBody.create(                       // Convert our JSON string into an OkHttp request body
                    requestBodyJson.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()                      // Builder pattern - assemble the HTTP request piece by piece
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + GROQ_API_KEY.trim()) // Proves who's making the request
                    .addHeader("Content-Type", "application/json")
                    .post(body)                                           // This is a POST request carrying our JSON
                    .build();

            // enqueue() sends this request ASYNCHRONOUSLY - on a background
            // thread, so it doesn't freeze the app's UI while waiting.
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {        // Network-level failure (no internet, timeout, etc.)
                    runOnUiThread(() -> {                                  // Hop back to the UI thread before touching any views
                        list.remove(list.size() - 1);                     // Remove the "Thinking..." placeholder
                        list.add(new ChatMessage("Network error. Please check connection.", false));
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) { // HTTP 2xx AND has a body
                        try {
                            String respStr = response.body().string();    // Read the raw JSON response text
                            JSONObject jsonResp = new JSONObject(respStr); // Parse it into a JSONObject
                            String reply = jsonResp.getJSONArray("choices") // Navigate down the response's JSON structure...
                                    .getJSONObject(0)                      // ...first (and only) choice...
                                    .getJSONObject("message")              // ...its message object...
                                    .getString("content");                 // ...and finally the actual text reply

                            runOnUiThread(() -> {
                                list.remove(list.size() - 1);              // Remove "Thinking..."
                                list.add(new ChatMessage(reply.trim(), false)); // Add the real reply in its place
                                adapter.notifyDataSetChanged();
                            });
                        } catch (Exception ex) {                            // JSON parsing could fail if the response shape is unexpected
                            runOnUiThread(() -> {
                                list.remove(list.size() - 1);
                                list.add(new ChatMessage("Failed to parse response.", false));
                                adapter.notifyDataSetChanged();
                            });
                        }
                    } else {                                                // HTTP error status (4xx/5xx) or empty body
                        runOnUiThread(() -> {
                            list.remove(list.size() - 1);
                            list.add(new ChatMessage("API Error (" + response.code() + ").", false));
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            });
        } catch (Exception e) {                                           // Catches any failure while BUILDING the request itself
            list.remove(list.size() - 1);
            list.add(new ChatMessage("Error generating request.", false));
            adapter.notifyDataSetChanged();
        }
    }

    // Sends a "play this track" command to AudioService.
    private void startAudioService(String track) {
        try {
            Intent intent = new Intent(this, AudioService.class);        // Explicit Intent targeting exactly this service class
            intent.setAction(AudioService.ACTION_PLAY);                  // Attach which action to perform
            intent.putExtra(AudioService.EXTRA_TRACK, track);            // Attach which track to play
            startService(intent);                                         // Actually send it
        } catch (Exception e) {
            Log.e("AUDIO", "Service start error", e);
        }
    }

    // Sends a "stop everything" command to AudioService.
    private void stopAudioService() {
        try {
            Intent intent = new Intent(this, AudioService.class);
            intent.setAction(AudioService.ACTION_STOP);
            startService(intent);
        } catch (Exception e) {
            Log.e("AUDIO", "Service stop error", e);
        }
    }

    // Convenience wrapper around TextToSpeech, with a readiness guard.
    private void speakText(String text) {
        if (textToSpeech != null && isTtsReady) {                        // Don't attempt to speak before TTS finished initializing
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FocusTTS"); // QUEUE_FLUSH interrupts anything currently speaking
        }
    }

    // Called by Android once TextToSpeech has finished setting itself up.
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);            // Configure the spoken language
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true;                                        // Only now is it safe to actually call speak()
            }
        }
    }

    // Final cleanup before this Activity is fully destroyed.
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();                                     // Releases TextToSpeech's native audio resources
        }
        stopAudioService();                                               // Make sure background audio doesn't keep playing
        super.onDestroy();                                                // Let the parent class finish its own cleanup
    }
}