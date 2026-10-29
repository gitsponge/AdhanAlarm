package islam.adhanalarm.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import islam.adhanalarm.App;
import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.NotificationHandler;

/**
 * Plays the adhan, chime or bismillah. It runs as a foreground service so Android keeps it alive
 * until the recording ends, which Android 17 requires for any audio started in the background.
 */
public class AdhanService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private static final String ACTION_PLAY = "islam.adhanalarm.action.PLAY";
    private static final String EXTRA_SOUND = "sound";
    private static final short BISMILLAH = -1;
    private static final int SOUND_ADHAN = 0, SOUND_CHIME = 1, SOUND_BISMILLAH = 2;

    // Where a short adhan stops: the built-in adhan's opening takbirs end in a pause at 26 s, the fajr adhan's
    // run straight on so it fades out by 28.5 s, and a sound from the phone fades out by 30 s
    private static final int SHORT_ADHAN_MS = 26000, SHORT_FAJR_ADHAN_MS = 27000, SHORT_OWN_SOUND_MS = 28000;
    private static final int FADE_MS = 1500, FADE_STEP_MS = 100;

    private static boolean sKeepNotification = true;

    private MediaPlayer mPlayer;
    private AudioFocusRequest mFocusRequest;
    private short mTimeIndex = BISMILLAH;
    private long mTime;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * Returns false if Android didn't allow the service to start, so the caller can fall back to a plain notification
     */
    public static boolean play(Context context, short timeIndex, long time, boolean adhan) {
        return start(context, timeIndex, time, adhan ? SOUND_ADHAN : SOUND_CHIME);
    }

    public static void playBismillah(Context context) {
        start(context, BISMILLAH, System.currentTimeMillis(), SOUND_BISMILLAH);
    }

    private static boolean start(Context context, short timeIndex, long time, int sound) {
        Intent intent = new Intent(context, AdhanService.class)
                .setAction(ACTION_PLAY)
                .putExtra(CONSTANT.EXTRA_TIME_INDEX, timeIndex)
                .putExtra(CONSTANT.EXTRA_ACTUAL_TIME, time)
                .putExtra(EXTRA_SOUND, sound);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException ex) {
            // Android 12+ only lets an app in the background start this from an exact alarm
            return false;
        }
    }

    /**
     * Stops playing. With keepNotification the prayer's notification stays in the shade.
     */
    public static void stop(Context context, boolean keepNotification) {
        sKeepNotification = keepNotification;
        context.stopService(new Intent(context, AdhanService.class));
    }

    /**
     * The phone sound chosen for this prayer's adhan, or null for the built-in one
     */
    public static Uri getChosenSound(SharedPreferences settings, short timeIndex) {
        String uri = settings.getString(timeIndex == CONSTANT.FAJR ? Prefs.FAJR_ADHAN_SOUND : Prefs.ADHAN_SOUND, "");
        return uri.isEmpty() ? null : Uri.parse(uri);
    }

    public static int getBuiltInAdhan(short timeIndex) {
        return timeIndex == CONSTANT.FAJR ? R.raw.adhan_fajr : R.raw.adhan;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_PLAY.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mTimeIndex = intent.getShortExtra(CONSTANT.EXTRA_TIME_INDEX, BISMILLAH);
        mTime = intent.getLongExtra(CONSTANT.EXTRA_ACTUAL_TIME, System.currentTimeMillis());
        sKeepNotification = mTimeIndex != BISMILLAH;
        Notification notification = mTimeIndex == BISMILLAH ? NotificationHandler.buildBismillah(this) : NotificationHandler.buildPlaying(this, mTimeIndex, mTime);
        ServiceCompat.startForeground(this, NotificationHandler.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (!startPlayback(intent.getIntExtra(EXTRA_SOUND, SOUND_ADHAN))) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean startPlayback(int sound) {
        releasePlayer();
        abandonAudioFocus();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(sound == SOUND_CHIME ? AudioAttributes.CONTENT_TYPE_SONIFICATION : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (!requestAudioFocus(audioManager, attributes)) {
            return false;
        }
        int shortStopMs = 0;
        if (sound == SOUND_ADHAN) {
            SharedPreferences settings = App.getSettings(this);
            Uri chosen = getChosenSound(settings, mTimeIndex);
            if (chosen != null) {
                mPlayer = createPlayer(chosen, attributes);
            }
            boolean ownSound = mPlayer != null;
            if (mPlayer == null) { // None chosen, or the file can't be read (it was deleted, or the phone hasn't been unlocked since starting)
                mPlayer = MediaPlayer.create(this, getBuiltInAdhan(mTimeIndex), attributes, audioManager.generateAudioSessionId());
            }
            if (settings.getBoolean(Prefs.SHORT_ADHAN, false)) {
                shortStopMs = ownSound ? SHORT_OWN_SOUND_MS : mTimeIndex == CONSTANT.FAJR ? SHORT_FAJR_ADHAN_MS : SHORT_ADHAN_MS;
            }
        } else {
            mPlayer = MediaPlayer.create(this, sound == SOUND_CHIME ? R.raw.chime : R.raw.bismillah, attributes, audioManager.generateAudioSessionId());
        }
        if (mPlayer == null) {
            return false;
        }
        mPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.start();
        if (shortStopMs > 0) {
            mHandler.postDelayed(new Fade(), shortStopMs);
        }
        return true;
    }

    private MediaPlayer createPlayer(Uri uri, AudioAttributes attributes) {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(attributes);
            player.setDataSource(this, uri);
            player.prepare();
            return player;
        } catch (Exception ex) {
            player.release();
            return null;
        }
    }

    /**
     * Turns the volume down step by step, then stops
     */
    private class Fade implements Runnable {
        private float mVolume = 1f;

        @Override
        public void run() {
            if (mPlayer == null) return;
            mVolume -= (float) FADE_STEP_MS / FADE_MS;
            if (mVolume <= 0) {
                stopSelf();
                return;
            }
            mPlayer.setVolume(mVolume, mVolume);
            mHandler.postDelayed(this, FADE_STEP_MS);
        }
    }

    @SuppressWarnings("deprecation") // The older calls are only for Android 7 and below
    private boolean requestAudioFocus(AudioManager audioManager, AudioAttributes attributes) {
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            result = audioManager.requestAudioFocus(mFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(mFocusRequest);
                mFocusRequest = null;
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    private void releasePlayer() {
        mHandler.removeCallbacksAndMessages(null);
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stopSelf(); // For example a phone call started
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        stopSelf();
        return true;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        abandonAudioFocus();
        ServiceCompat.stopForeground(this, sKeepNotification ? ServiceCompat.STOP_FOREGROUND_DETACH : ServiceCompat.STOP_FOREGROUND_REMOVE);
        if (sKeepNotification && mTimeIndex != BISMILLAH) {
            NotificationHandler.notify(this, NotificationHandler.buildPlayed(this, mTimeIndex, mTime));
        }
        sKeepNotification = true;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
