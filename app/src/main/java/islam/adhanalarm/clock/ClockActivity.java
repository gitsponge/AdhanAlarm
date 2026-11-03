package islam.adhanalarm.clock;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import islam.adhanalarm.App;
import islam.adhanalarm.MainActivity;
import islam.adhanalarm.Prefs;
import islam.adhanalarm.R;
import islam.adhanalarm.auto.AutoMediaService;

/**
 * The prayer clock: the dial full screen, for a phone on a stand, a tablet, a Chromebook or a TV, where it's
 * what the app opens to. The screen stays on only while the device charges, and from the middle of the night
 * until fajr it dims right down; a tap brings it back up for a minute. On a TV, OK opens the rest of the app.
 */
public class ClockActivity extends AppCompatActivity implements ClockController.Host {

    private static final float NIGHT_BRIGHTNESS = 0.02f, NIGHT_ALPHA = 0.8f;
    private static final long BRIGHT_FOR_MS = 60000, HINT_MS = 6000;

    private ClockController mController;
    private View mRoot;
    private boolean mNight;
    private long mBrightUntil;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDimAgain = new Runnable() {
        @Override
        public void run() {
            applyBrightness();
        }
    };
    private final BroadcastReceiver mPower = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            keepScreenOn(Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!App.getSettings(this).getBoolean(Prefs.SETUP_DONE, false)) {
            // Opened first from a TV's home screen: the app asks where it is before it can show any times
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.clock);
        mRoot = findViewById(R.id.clock);
        // Full screen: the bars come back with a swipe from the edge
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.systemBars());
        mController = new ClockController(this, mRoot, true, this);
        if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(getIntent().getAction())) {
            playFromSearch(getIntent().getStringExtra(SearchManager.QUERY));
        }
        if (isTelevision()) {
            // How to reach the rest of the app with the remote, for a few seconds
            final View hint = findViewById(R.id.clock_hint);
            hint.setVisibility(View.VISIBLE);
            hint.animate().alpha(1).setStartDelay(600).setDuration(400).withEndAction(new Runnable() {
                @Override
                public void run() {
                    hint.animate().alpha(0).setStartDelay(HINT_MS).setDuration(600);
                }
            });
        }
        mRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mNight) return;
                mBrightUntil = System.currentTimeMillis() + BRIGHT_FOR_MS;
                applyBrightness();
                mHandler.removeCallbacks(mDimAgain);
                mHandler.postDelayed(mDimAgain, BRIGHT_FOR_MS);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mController.start();
        IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ContextCompat.registerReceiver(this, mPower, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Intent battery = ContextCompat.registerReceiver(this, null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        keepScreenOn(battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
    }

    @Override
    protected void onStop() {
        mController.stop();
        unregisterReceiver(mPower);
        mHandler.removeCallbacks(mDimAgain);
        super.onStop();
    }

    /** Asked by voice to play the adhan: the same player the car uses plays it */
    private void playFromSearch(final String query) {
        SessionToken token = new SessionToken(this, new ComponentName(this, AutoMediaService.class));
        final ListenableFuture<MediaController> connecting = new MediaController.Builder(this, token).buildAsync();
        connecting.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaController controller = connecting.get();
                    controller.setMediaItem(new MediaItem.Builder()
                            .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setSearchQuery(query == null ? "" : query).build())
                            .build());
                    controller.prepare();
                    controller.play();
                    controller.release(); // It plays on in the service
                } catch (Exception ex) {
                    // The player couldn't be reached; the clock still shows
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** On a TV, OK opens today's times and the settings; any other key, or Back, as usual */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_MENU:
                startActivity(new Intent(this, MainActivity.class));
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private boolean isTelevision() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override
    public void onNightChanged(boolean night) {
        mNight = night;
        applyBrightness();
    }

    /** On while it charges; on battery the screen times out as usual */
    private void keepScreenOn(boolean charging) {
        if (charging) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void applyBrightness() {
        boolean dim = mNight && System.currentTimeMillis() >= mBrightUntil;
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = dim ? NIGHT_BRIGHTNESS : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
        mRoot.setAlpha(dim ? NIGHT_ALPHA : 1f);
    }
}
