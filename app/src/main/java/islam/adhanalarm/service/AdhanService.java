package islam.adhanalarm.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import islam.adhanalarm.CONSTANT;
import islam.adhanalarm.R;
import islam.adhanalarm.handler.NotificationHandler;

/**
 * Plays the adhan, beep or bismillah. It runs as a foreground service so Android keeps it alive
 * until the recording ends, which Android 17 requires for any audio started in the background.
 */
public class AdhanService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private static final String ACTION_PLAY = "islam.adhanalarm.action.PLAY";
    private static final String EXTRA_RES_ID = "resId";
    private static final short BISMILLAH = -1;

    private static boolean sKeepNotification = true;

    private MediaPlayer mPlayer;
    private AudioFocusRequest mFocusRequest;
    private short mTimeIndex = BISMILLAH;

    /**
     * Returns false if Android didn't allow the service to start, so the caller can fall back to a plain notification
     */
    public static boolean play(Context context, short timeIndex, int resId) {
        Intent intent = new Intent(context, AdhanService.class)
                .setAction(ACTION_PLAY)
                .putExtra(CONSTANT.EXTRA_TIME_INDEX, timeIndex)
                .putExtra(EXTRA_RES_ID, resId);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException ex) {
            // Android 12+ only lets an app in the background start this from an exact alarm
            return false;
        }
    }

    public static void playBismillah(Context context) {
        play(context, BISMILLAH, R.raw.bismillah);
    }

    /**
     * Stops playing. With keepNotification the prayer's notification stays in the shade.
     */
    public static void stop(Context context, boolean keepNotification) {
        sKeepNotification = keepNotification;
        context.stopService(new Intent(context, AdhanService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_PLAY.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mTimeIndex = intent.getShortExtra(CONSTANT.EXTRA_TIME_INDEX, BISMILLAH);
        sKeepNotification = mTimeIndex != BISMILLAH;
        Notification notification = mTimeIndex == BISMILLAH ? NotificationHandler.buildBismillah(this) : NotificationHandler.buildPlaying(this, mTimeIndex);
        ServiceCompat.startForeground(this, NotificationHandler.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (!startPlayback(intent.getIntExtra(EXTRA_RES_ID, R.raw.adhan))) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean startPlayback(int resId) {
        releasePlayer();
        abandonAudioFocus();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (!requestAudioFocus(audioManager, attributes)) {
            return false;
        }
        mPlayer = MediaPlayer.create(this, resId, attributes, audioManager.generateAudioSessionId());
        if (mPlayer == null) {
            return false;
        }
        mPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnErrorListener(this);
        mPlayer.start();
        return true;
    }

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
            NotificationHandler.notify(this, NotificationHandler.buildPlayed(this, mTimeIndex));
        }
        sKeepNotification = true;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
