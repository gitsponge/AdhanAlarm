package islam.adhanalarm.clock;

import android.annotation.SuppressLint;
import android.service.dreams.DreamService;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import islam.adhanalarm.R;

/**
 * The prayer clock as the phone's screen saver, while it charges or sits in a dock: chosen under Screen
 * saver in the phone's display settings. Any touch wakes the phone as usual, so there's no Stop button; the
 * adhan's notification has one. From the middle of the night until fajr it dims.
 */
public class PrayerClockDream extends DreamService implements ClockController.Host {

    private static final float NIGHT_ALPHA = 0.8f;

    private ClockController mController;
    private View mRoot;

    @Override
    @SuppressLint("InflateParams") // A screen saver's window has no parent to inflate into
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        // A service has no theme of its own, so the clock's is applied for its layout
        mRoot = LayoutInflater.from(new ContextThemeWrapper(this, R.style.ClockTheme)).inflate(R.layout.clock, null);
        setContentView(mRoot);
        mController = new ClockController(this, mRoot, false, this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        mController.start();
    }

    @Override
    public void onDreamingStopped() {
        mController.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onNightChanged(boolean night) {
        setScreenBright(!night);
        mRoot.setAlpha(night ? NIGHT_ALPHA : 1f);
    }
}
