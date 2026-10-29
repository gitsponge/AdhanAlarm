package islam.adhanalarm;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.Arrays;
import java.util.Map;

import islam.adhanalarm.handler.LocationHandler;

/**
 * The first run: where you are, how the times are worked out, and how prayers call you
 */
public class SetupActivity extends AppCompatActivity {

    private static final int PAGES = 3;
    private static final long LOCATION_TIMEOUT_MS = 20000;
    private static final short[] ALERT_CHOICES = {CONSTANT.NOTIFICATION_PLAY, CONSTANT.NOTIFICATION_BEEP, CONSTANT.NOTIFICATION_DEFAULT};
    private static final int[] ALERT_ICONS = {R.drawable.ic_alert_adhan, R.drawable.ic_alert_chime, R.drawable.ic_alert_notification};
    private static final int[] ALERT_LABELS = {R.string.setup_alerts_adhan, R.string.setup_alerts_chime, R.string.setup_alerts_notification};
    private static final int[] ALERT_HINTS = {R.string.setup_alerts_adhan_hint, R.string.alert_chime_hint, R.string.alert_notification_hint};

    private SharedPreferences mSettings;
    private ViewFlipper mPages;
    private LocationHandler mLocationHandler;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mFinding;
    private int mAlertChoice = 0;

    private final Runnable mLocationTimeout = new Runnable() {
        @Override
        public void run() {
            mFinding = false;
            setStatus(getString(R.string.location_unavailable));
        }
    };

    private final ActivityResultLauncher<String[]> mLocationPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            if (LocationHandler.hasPermission(SetupActivity.this)) {
                findLocation();
            } else {
                setStatus(getString(R.string.location_permission_needed));
            }
        }
    });

    private final ActivityResultLauncher<String> mNotificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean granted) {
            finishSetup(); // Alarm check in settings points it out if notifications stay off
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        mSettings = App.getSettings(this);
        mPages = findViewById(R.id.setup_pages);
        findViewById(R.id.setup_icon).setClipToOutline(true); // The mosque stands on the circle's edge, like the app icon

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setup_container), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });

        mLocationHandler = new LocationHandler(this);
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location location) {
                if (!mFinding) return;
                mFinding = false;
                mHandler.removeCallbacks(mLocationTimeout);
                mSettings.edit().putBoolean(Prefs.AUTO_LOCATION, true).apply();
                Locations.save(SetupActivity.this, location.getLatitude(), location.getLongitude(), new Runnable() {
                    @Override
                    public void run() {
                        showPlace();
                    }
                });
            }
        });
        findViewById(R.id.setup_use_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (LocationHandler.hasPermission(SetupActivity.this)) {
                    findLocation();
                } else {
                    mLocationPermission.launch(LocationHandler.PERMISSIONS);
                }
            }
        });
        findViewById(R.id.setup_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SetupActivity.this, LocationActivity.class));
            }
        });

        findViewById(R.id.setup_method_change).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseMethod();
            }
        });
        MaterialButtonToggleGroup asr = findViewById(R.id.setup_asr);
        asr.check(Prefs.isHanafi(mSettings) ? R.id.setup_asr_hanafi : R.id.setup_asr_standard);
        asr.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) mSettings.edit().putString(Prefs.ASR, checkedId == R.id.setup_asr_hanafi ? Prefs.ASR_HANAFI : "standard").apply();
            }
        });

        buildAlertChoices();

        findViewById(R.id.setup_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                next();
            }
        });
        findViewById(R.id.setup_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPage(mPages.getDisplayedChild() - 1);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mPages.getDisplayedChild() > 0) {
                    showPage(mPages.getDisplayedChild() - 1);
                } else {
                    finish();
                }
            }
        });
        showPage(savedInstanceState != null ? savedInstanceState.getInt("page", 0) : 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showPlace(); // A city may have been chosen in the search
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", mPages.getDisplayedChild());
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean hasLocation() {
        return mSettings.contains(Prefs.LATITUDE);
    }

    private void findLocation() {
        if (!LocationHandler.isEnabled(this)) {
            setStatus(getString(R.string.location_off));
            return;
        }
        mFinding = true;
        setStatus(getString(R.string.finding_location));
        mHandler.removeCallbacks(mLocationTimeout);
        mHandler.postDelayed(mLocationTimeout, LOCATION_TIMEOUT_MS);
        mLocationHandler.update(true);
    }

    private void setStatus(String status) {
        ((TextView) findViewById(R.id.setup_location_status)).setText(status);
    }

    private void showPlace() {
        if (hasLocation() && !mFinding) {
            setStatus(getString(R.string.location_summary, Formats.place(this, mSettings), Formats.placeNote(this, mSettings)));
        }
        updateFooter();
    }

    private void showPage(int page) {
        if (page < 0 || page >= PAGES) return;
        mPages.setDisplayedChild(page);
        if (page == 1) showMethod();
        updateFooter();
    }

    private void updateFooter() {
        int page = mPages.getDisplayedChild();
        ((TextView) findViewById(R.id.setup_step)).setText(getString(R.string.setup_step, page + 1, PAGES));
        findViewById(R.id.setup_back).setVisibility(page > 0 ? View.VISIBLE : View.INVISIBLE);
        TextView next = findViewById(R.id.setup_next);
        next.setText(page == PAGES - 1 ? R.string.setup_start : R.string.next);
        next.setEnabled(page > 0 || hasLocation()); // Times for the wrong place are worse than none, so a place comes first
    }

    private void next() {
        int page = mPages.getDisplayedChild();
        if (page < PAGES - 1) {
            showPage(page + 1);
            return;
        }
        // Each prayer takes the chosen alert; sunrise isn't a prayer, so it stays quiet
        for (short prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) {
            Prefs.setAlert(mSettings, prayer, prayer == CONSTANT.SUNRISE ? CONSTANT.NOTIFICATION_NONE : ALERT_CHOICES[mAlertChoice]);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            finishSetup();
        }
    }

    private void finishSetup() {
        mSettings.edit().putBoolean(Prefs.SETUP_DONE, true).apply();
        App.broadcastPrayerTimeUpdate();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showMethod() {
        String method = Prefs.getMethod(mSettings);
        int index = Arrays.asList(getResources().getStringArray(R.array.method_values)).indexOf(method);
        CharSequence[] names = getResources().getTextArray(R.array.method_names);
        ((TextView) findViewById(R.id.setup_method_name)).setText(names[Math.max(0, index)]);
        double[] parameters = CONSTANT.getMethodParameters(method);
        ((TextView) findViewById(R.id.setup_method_angles)).setText(parameters[1] > 0
                ? getString(R.string.method_angles, Formats.number(parameters[0], 1), Formats.number(parameters[1], 1))
                : getString(R.string.method_angles_interval, Formats.number(parameters[0], 1), (int) parameters[2]));
    }

    private void chooseMethod() {
        final String[] values = getResources().getStringArray(R.array.method_values);
        int current = Arrays.asList(values).indexOf(Prefs.getMethod(mSettings));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.method)
                .setSingleChoiceItems(getResources().getTextArray(R.array.method_names), current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSettings.edit().putString(Prefs.METHOD, values[which]).putBoolean(Prefs.METHOD_CHOSEN, true).apply();
                        showMethod();
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void buildAlertChoices() {
        LinearLayout list = findViewById(R.id.setup_alerts);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < ALERT_CHOICES.length; i++) {
            View option = inflater.inflate(R.layout.alert_option, list, false);
            ((ImageView) option.findViewById(R.id.option_icon)).setImageResource(ALERT_ICONS[i]);
            ((TextView) option.findViewById(R.id.option_label)).setText(ALERT_LABELS[i]);
            ((TextView) option.findViewById(R.id.option_hint)).setText(ALERT_HINTS[i]);
            final int choice = i;
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mAlertChoice = choice;
                    showAlertChoices();
                }
            });
            list.addView(option);
        }
        showAlertChoices();
    }

    private void showAlertChoices() {
        LinearLayout list = findViewById(R.id.setup_alerts);
        for (int i = 0; i < list.getChildCount(); i++) {
            View option = list.getChildAt(i);
            final boolean checked = i == mAlertChoice;
            ((MaterialRadioButton) option.findViewById(R.id.option_radio)).setChecked(checked);
            option.setBackgroundResource(checked ? R.drawable.option_selected : 0);
            option.setContentDescription(getString(ALERT_LABELS[i]) + ", " + getString(ALERT_HINTS[i]));
            ViewCompat.setAccessibilityDelegate(option, new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true);
                    info.setChecked(checked ? AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE : AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE);
                }
            });
        }
    }
}
