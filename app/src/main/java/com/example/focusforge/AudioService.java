package com.example.focusforge;

import android.app.Service;                                     // Base class for background components with no UI
import android.content.Intent;
import android.media.MediaPlayer;                                // Android's class for playing audio files
import android.os.IBinder;
import android.util.Log;                                         // For writing diagnostic messages to Logcat

import androidx.annotation.Nullable;

// A Service runs in the background, independent of any single screen's
// lifecycle - perfect for ambient audio that should keep playing even if
// the user briefly looks at another screen within the app.
public class AudioService extends Service {

    private static final String TAG = "AudioService";            // Label used to filter this class's Logcat output

    // Action strings: these identify WHAT the Intent wants this service to do
    // when it arrives in onStartCommand() below.
    public static final String ACTION_PLAY = "com.example.focusforge.ACTION_PLAY";
    public static final String ACTION_STOP = "com.example.focusforge.ACTION_STOP";
    public static final String EXTRA_TRACK = "extra_track";      // Key used to attach "which track" data to the Intent

    // Track keys - each one must exactly match a filename in res/raw/
    // (without the .mp3 extension), since we look these up dynamically below.
    public static final String TRACK_LOFI = "lo_fi";
    public static final String TRACK_RAIN = "rain";
    public static final String TRACK_WHITE_NOISE = "white_noise";

    private MediaPlayer mediaPlayer;                              // The actual audio player instance, or null when nothing is playing
    private String currentTrack = null;                           // Tracks which track key is currently loaded

    // Called every time something calls startService() targeting this class.
    // This is where we read what action/data was sent and react to it.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {       // Defensive check - Intent or its action could be null
            Log.d(TAG, "onStartCommand action=" + intent.getAction());

            if (ACTION_PLAY.equals(intent.getAction())) {         // Caller wants us to start/switch playback
                String track = intent.getStringExtra(EXTRA_TRACK); // Pull out which track was requested
                if (track == null) track = TRACK_LOFI;            // Fall back to Lo-Fi if none was specified
                Log.d(TAG, "Requested track: " + track);
                playTrack(track);

            } else if (ACTION_STOP.equals(intent.getAction())) {  // Caller wants us to stop entirely
                Log.d(TAG, "Stopping playback");
                stopPlayback();
                stopSelf();                                       // Tell Android this service can be destroyed now
            }
        } else {
            Log.w(TAG, "onStartCommand called with null intent or null action"); // Shouldn't normally happen
        }
        return START_NOT_STICKY;
        // START_NOT_STICKY means: if Android kills this service to reclaim memory,
        // don't automatically try to restart it - the user will restart it themselves
        // by tapping a sound option again.
    }

    // The core logic: load and start playing a given track, swapping out
    // whatever was playing before if necessary.
    private void playTrack(String track) {
        if (mediaPlayer != null && mediaPlayer.isPlaying() && track.equals(currentTrack)) {
            Log.d(TAG, "Track '" + track + "' already playing, ignoring duplicate request");
            return;                                               // Nothing to do - this exact track is already going
        }

        releasePlayer();                                          // Stop and free whatever was playing before

        // getIdentifier() looks up a resource by NAME at runtime instead of
        // needing a compile-time R.raw.xxx reference - this is what lets us
        // support any track name dynamically, as long as the file exists.
        int resId = getResources().getIdentifier(track, "raw", getPackageName());
        Log.d(TAG, "Resolved raw resource id for '" + track + "': " + resId + " (0 means NOT FOUND)");
        if (resId == 0) {                                         // 0 is the "not found" sentinel value
            Log.e(TAG, "No raw resource named '" + track + "' found...");
            return;                                                // Fail silently - nothing plays, but app doesn't crash
        }

        try {
            mediaPlayer = MediaPlayer.create(this, resId);        // Create and prepare the player for this resource
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer.create() threw an exception", e);
            return;
        }

        if (mediaPlayer == null) {                                // create() can return null on failure instead of throwing
            Log.e(TAG, "MediaPlayer.create() returned null for '" + track + "'...");
            return;
        }

        mediaPlayer.setLooping(true);                             // Loop forever instead of stopping after one play-through
        mediaPlayer.start();                                      // Actually begin playback
        currentTrack = track;                                     // Remember what's now playing, for the check at the top of this method
        Log.d(TAG, "Playback started successfully for '" + track + "', isPlaying=" + mediaPlayer.isPlaying());
    }

    // Fully stops playback (used when Mute is selected, or a session ends).
    private void stopPlayback() {
        releasePlayer();
        currentTrack = null;
    }

    // Shared cleanup logic: stop and release the MediaPlayer's system resources.
    private void releasePlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();                                // Frees native audio resources - essential to avoid leaks
            mediaPlayer = null;                                   // Clear the reference so we don't accidentally reuse a dead player
        }
    }

    // Called when the whole Service is being destroyed - final cleanup safety net.
    @Override
    public void onDestroy() {
        Log.d(TAG, "AudioService onDestroy");
        releasePlayer();
        super.onDestroy();
    }

    // Required by the Service base class. Returning null means this service
    // doesn't support being "bound" to (a different, more complex interaction
    // pattern than the simple startService()/Intent approach used here).
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}