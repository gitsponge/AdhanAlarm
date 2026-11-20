package islam.adhanalarm.clock;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
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
 * The prayer dial full screen, for a phone on a stand, a tablet, a Chromebook or a TV, where it's what the app
 * opens to. A tap brings back the phone's bars with an arrow back to Today, for a few seconds. The screen stays
 * on only while the device charges, and from the middle of the night until fajr it dims right down; a tap
 * brings it back up for a minute. On a TV, OK opens the rest of the app.
 */
public class ClockActivity extends AppCompatActivity implements ClockController.Host {

    private static final float NIGHT_BRIGHTNESS = 0.02f, NIGHT_ALPHA = 0.8f;
    private static final long BRIGHT_FOR_MS = 60000, HINT_MS = 6000;
    private static final int CONTROLS_MS = 4000;

    private ClockController mController;
    private View mRoot, mControls;
    private WindowInsetsControllerCompat mBars;
    private boolean mNight, mControlsShown;
    private long mBrightUntil;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDimAgain = new Runnable() {
        @Override
        public void run() {
            applyBrightness();
        }
    };
    private final Runnable mHideControls = new Runnable() {
        @Override
        public void run() {
            hideControls(true);
        }
    };
    private final BroadcastReceiver mPower = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            keepScreenOn(Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()));
        }
    };

    /**
     * Opens the dial full screen from outside the app, as from the widget or the car, with Today beneath it, so
     * Back lands on Today just as if the app had been opened from its icon
     */
    public static PendingIntent withToday(Context context) {
        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(new Intent(context, ClockActivity.class))
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!App.getSettings(this).getBoolean(Prefs.SETUP_DONE, false)) {
            // The app asks where it is before it can show any times. Opened first from a TV's home screen, the
            // dial sends it there; from the widget or the shortcut Today is already beneath, and asks itself.
            if (isTaskRoot()) startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_clock);
        mRoot = findViewById(R.id.clock);
        mControls = findViewById(R.id.clock_controls);
        // Full screen. A tap brings the bars back with the back arrow for a few seconds; a swipe from the edge
        // brings back the bars alone, as anywhere else.
        mBars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        mBars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        mBars.hide(WindowInsetsCompat.Type.systemBars());
        placeControls();
        findViewById(R.id.clock_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                upToToday();
            }
        });
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
                showControls();
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
        // Back to the dial alone each time it shows, except with TalkBack on, where the arrow stays
        if (isTouchExploring()) {
            showControls();
        } else {
            hideControls(false);
        }
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
        mHandler.removeCallbacks(mHideControls);
        super.onStop();
    }

    /** On a Chromebook, or a tablet with a mouse, moving the mouse brings up the arrow as a tap does */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mControls != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
            showControls();
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /** The bars and the back arrow, for a few seconds, or for as long as TalkBack is on. A TV's remote has its own Back. */
    private void showControls() {
        if (isTelevision()) return;
        mHandler.removeCallbacks(mHideControls);
        if (!mControlsShown) {
            mControlsShown = true;
            mBars.show(WindowInsetsCompat.Type.systemBars());
            mControls.animate().cancel();
            mControls.setVisibility(View.VISIBLE);
            mControls.animate().alpha(1).setDuration(150);
        }
        if (!isTouchExploring()) mHandler.postDelayed(mHideControls, controlsMs());
    }

    private void hideControls(boolean fade) {
        mHandler.removeCallbacks(mHideControls);
        mControlsShown = false;
        mBars.hide(WindowInsetsCompat.Type.systemBars());
        mControls.animate().cancel();
        if (fade) {
            mControls.animate().alpha(0).setDuration(300).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mControls.setVisibility(View.INVISIBLE);
                }
            });
        } else {
            mControls.setAlpha(0);
            mControls.setVisibility(View.INVISIBLE);
        }
    }

    /** Longer for anyone who has asked Android for more time to act on what's shown */
    private int controlsMs() {
        AccessibilityManager accessibility = ContextCompat.getSystemService(this, AccessibilityManager.class);
        if (accessibility != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return accessibility.getRecommendedTimeoutMillis(CONTROLS_MS, AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        return CONTROLS_MS;
    }

    /** TalkBack exploring by touch, where a tap only says what's under the finger */
    private boolean isTouchExploring() {
        AccessibilityManager accessibility = ContextCompat.getSystemService(this, AccessibilityManager.class);
        return accessibility != null && accessibility.isTouchExplorationEnabled();
    }

    /** The arrow sits below where the status bar and any camera cut-out come, so it stays put as the bars show and hide */
    private void placeControls() {
        final int below = mControls.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mControls, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
                Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                view.setPadding(Math.max(bars.left, cutout.left), Math.max(bars.top, cutout.top), Math.max(bars.right, cutout.right), below);
                return insets;
            }
        });
    }

    /** The back arrow: up to Today, whichever way the dial was opened */
    private void upToToday() {
        Intent today = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (isTaskRoot() || shouldUpRecreateTask(today)) {
            // Opened on its own, as when the assistant plays the adhan: Today opens in its place
            TaskStackBuilder.create(this).addNextIntent(today).startActivities();
            finish();
        } else {
            navigateUpTo(today); // Today is beneath, from the app, the widget or the shortcut
        }
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
                    // The player couldn't be reached; the dial still shows
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
