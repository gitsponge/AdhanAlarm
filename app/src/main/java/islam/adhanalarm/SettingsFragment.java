package islam.adhanalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.LocaleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import islam.adhanalarm.handler.LocationHandler;
import islam.adhanalarm.service.AdhanService;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    // The keyboard for each text entry. AndroidX ignores android:inputType in settings.xml, so it's set here.
    static final Map<String, Integer> TEXT_ENTRIES = new HashMap<>();
    static {
        final int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        TEXT_ENTRIES.put(Prefs.ALTITUDE, decimal);
        TEXT_ENTRIES.put(Prefs.PRESSURE, decimal);
        TEXT_ENTRIES.put(Prefs.TEMPERATURE, decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
    }
    // The range each number has to be in. The keyboards still allow an empty entry, a lone minus sign or a lone point.
    static final Map<String, float[]> RANGES = new HashMap<>();
    static {
        RANGES.put(Prefs.ALTITUDE, new float[]{0, 9000}); // Metres
        RANGES.put(Prefs.PRESSURE, new float[]{100, 1100}); // Millibars
        RANGES.put(Prefs.TEMPERATURE, new float[]{-90, 60}); // Degrees Celsius
    }
    // Settings that change the times, so the alarms are set again when they change
    private static final List<String> SCHEDULE_KEYS = Arrays.asList(Prefs.METHOD, Prefs.ASR, Prefs.HIGH_LATITUDE, Prefs.HIJRI_ADJUST,
            Prefs.ROUNDING, Prefs.ALTITUDE, Prefs.PRESSURE, Prefs.TEMPERATURE, Prefs.TIME_FORMAT, Prefs.ARABIC_NAMES);
    private static final int[] ALERT_NAMES = {R.string.alert_off, R.string.alert_notification, R.string.alert_chime, R.string.alert_adhan};

    private SharedPreferences mSettings;
    private MediaPlayer mTestPlayer;
    private String mSoundKey = Prefs.ADHAN_SOUND;

    private final ActivityResultLauncher<String[]> mChooseSound = registerForActivityResult(new ActivityResultContracts.OpenDocument(), new ActivityResultCallback<Uri>() {
        @Override
        public void onActivityResult(Uri uri) {
            if (uri == null) return;
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mSettings.edit().putString(mSoundKey, uri.toString()).apply();
            } catch (SecurityException ex) {
                Toast.makeText(requireContext(), R.string.sound_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    });

    private final ActivityResultLauncher<String[]> mLocationPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            refreshSummaries();
        }
    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setStorageDeviceProtected(); // Where App.getSettings() reads them
        setPreferencesFromResource(R.xml.settings, rootKey);
        mSettings = getPreferenceManager().getSharedPreferences();
        fitPreferences(getPreferenceScreen());

        for (Map.Entry<String, Integer> textEntry : TEXT_ENTRIES.entrySet()) {
            final int inputType = textEntry.getValue();
            EditTextPreference preference = findPreference(textEntry.getKey());
            preference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(inputType);
                }
            });
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    String plain = Formats.plainNumber((String) newValue);
                    if (plain == null || !isInRange(preference.getKey(), plain)) {
                        Toast.makeText(requireContext(), R.string.invalid_value, Toast.LENGTH_SHORT).show();
                        return false; // Keeps the old value
                    }
                    if (!plain.equals(newValue)) {
                        ((EditTextPreference) preference).setText(plain); // Saved with plain digits, as the prayer times read it
                        return false;
                    }
                    return true;
                }
            });
        }

        // Shows the method in use, even before anyone has chosen one
        ListPreference method = findPreference(Prefs.METHOD);
        if (method.getValue() == null) method.setValue(Prefs.getMethod(mSettings));
        method.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                mSettings.edit().putBoolean(Prefs.METHOD_CHOSEN, true).apply();
                return true;
            }
        });

        ListPreference language = findPreference("language");
        String appLanguage = getAppLanguage(requireContext());
        language.setValue("in".equals(appLanguage) ? "id" : appLanguage); // Older Androids call Indonesian "in"
        language.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags((String) newValue));
                return true;
            }
        });

        findPreference(Prefs.AUTO_LOCATION).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                if (Boolean.TRUE.equals(newValue) && !LocationHandler.hasPermission(requireContext())) {
                    mLocationPermission.launch(LocationHandler.PERMISSIONS);
                }
                return true;
            }
        });

        onClick("location", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(requireContext(), LocationActivity.class));
            }
        });
        onClick("adjustTimes", new Runnable() {
            @Override
            public void run() {
                showAdjustTimes();
            }
        });
        onClick("prayerAlerts", new Runnable() {
            @Override
            public void run() {
                requireActivity().finish(); // Each prayer's alert is set from its icon on the Today screen
            }
        });
        onClick(Prefs.ADHAN_SOUND, new Runnable() {
            @Override
            public void run() {
                chooseSound(Prefs.ADHAN_SOUND);
            }
        });
        onClick(Prefs.FAJR_ADHAN_SOUND, new Runnable() {
            @Override
            public void run() {
                chooseSound(Prefs.FAJR_ADHAN_SOUND);
            }
        });
        onClick("testAdhan", new Runnable() {
            @Override
            public void run() {
                toggleTest();
            }
        });
        onClick("alarmCheck", new Runnable() {
            @Override
            public void run() {
                showAlarmCheck();
            }
        });
        onClick("privacyPolicy", new Runnable() {
            @Override
            public void run() {
                open("https://cantangroup.com/adhan-alarm/privacy/");
            }
        });
        onClick("sourceCode", new Runnable() {
            @Override
            public void run() {
                open("https://github.com/gitsponge/AdhanAlarm");
            }
        });
        onClick("credits", new Runnable() {
            @Override
            public void run() {
                new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.credits).setMessage(R.string.credits_text).setPositiveButton(android.R.string.ok, null).show();
            }
        });
        findPreference("version").setSummary(BuildConfig.VERSION_NAME);
    }

    private void onClick(String key, final Runnable action) {
        findPreference(key).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                action.run();
                return true;
            }
        });
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ex) {
            // No browser on this phone
        }
    }

    private static boolean isInRange(String key, String plainNumber) {
        float[] range = RANGES.get(key);
        float number = Float.parseFloat(plainNumber);
        return number >= range[0] && number <= range[1];
    }

    @Override
    public void onResume() {
        super.onResume();
        mSettings.registerOnSharedPreferenceChangeListener(this);
        refreshSummaries();
    }

    @Override
    public void onPause() {
        mSettings.unregisterOnSharedPreferenceChangeListener(this);
        stopTest();
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key == null) return;
        if (Prefs.THEME.equals(key)) {
            Prefs.applyTheme(mSettings);
        }
        if (SCHEDULE_KEYS.contains(key) || key.startsWith(Prefs.OFFSET)) {
            App.broadcastPrayerTimeUpdate();
        }
        refreshSummaries();
    }

    private void refreshSummaries() {
        Context context = requireContext();
        findPreference("location").setSummary(getString(R.string.location_summary, Formats.place(context, mSettings), Formats.placeNote(context, mSettings)));

        String method = Prefs.getMethod(mSettings);
        double[] parameters = CONSTANT.getMethodParameters(method);
        ListPreference methodPreference = findPreference(Prefs.METHOD);
        String angles = parameters[1] > 0
                ? getString(R.string.method_angles, Formats.number(parameters[0], 1), Formats.number(parameters[1], 1))
                : getString(R.string.method_angles_interval, Formats.number(parameters[0], 1), (int) parameters[2]);
        methodPreference.setSummary(getString(R.string.method_summary, methodPreference.getEntry(), angles));

        // Diyanet's times are rounded as its timetable rounds them, so the setting doesn't apply
        ListPreference rounding = findPreference(Prefs.ROUNDING);
        boolean diyanet = CONSTANT.METHOD_DIYANET.equals(method);
        rounding.setEnabled(!diyanet);
        rounding.setSummary(diyanet ? methodPreference.getEntry() : rounding.getEntry());

        ListPreference highLatitude = findPreference(Prefs.HIGH_LATITUDE);
        highLatitude.setSummary(getString(R.string.high_latitude_summary, highLatitude.getEntry()));

        ListPreference hijri = findPreference(Prefs.HIJRI_ADJUST);
        hijri.setSummary(getString(R.string.hijri_summary, new islam.adhanalarm.handler.ScheduleHandler(mSettings).getHijriDate(context), hijri.getEntry()));

        List<String> adjustments = new ArrayList<>();
        for (short prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) {
            int offset = Prefs.getOffset(mSettings, prayer);
            if (offset != 0) adjustments.add(getString(R.string.name_and_time, getString(CONSTANT.TIME_NAMES[prayer]), Formats.adjustment(context, offset)));
        }
        findPreference("adjustTimes").setSummary(adjustments.isEmpty() ? getString(R.string.adjust_times_none) : TextUtils.join(getString(R.string.list_separator), adjustments));

        findPreference("prayerAlerts").setSummary(alertSummary());
        findPreference(Prefs.ADHAN_SOUND).setSummary(soundName(Prefs.ADHAN_SOUND, R.string.sound_built_in));
        findPreference(Prefs.FAJR_ADHAN_SOUND).setSummary(soundName(Prefs.FAJR_ADHAN_SOUND, R.string.sound_built_in_fajr));
        findPreference("testAdhan").setSummary(mTestPlayer != null ? R.string.test_adhan_playing : R.string.test_adhan_summary);

        int issues = countIssues(context);
        findPreference("alarmCheck").setSummary(issues == 0 ? getString(R.string.alarm_check_ok) : getResources().getQuantityString(R.plurals.alarm_check_issues, issues, issues));

        findPreference(Prefs.ALTITUDE).setSummary(getString(R.string.altitude_summary, mSettings.getString(Prefs.ALTITUDE, "0")));
        findPreference(Prefs.PRESSURE).setSummary(getString(R.string.pressure_summary, mSettings.getString(Prefs.PRESSURE, "1010")));
        findPreference(Prefs.TEMPERATURE).setSummary(getString(R.string.temperature_summary, mSettings.getString(Prefs.TEMPERATURE, "10")));
    }

    /** For example "Adhan: Fajr, Maghrib, Isha · Notification: Dhuhr, Asr" */
    private String alertSummary() {
        List<String> parts = new ArrayList<>();
        for (short type = CONSTANT.NOTIFICATION_PLAY; type >= CONSTANT.NOTIFICATION_NONE; type--) {
            List<String> names = new ArrayList<>();
            for (short prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) {
                if (prayer != CONSTANT.SUNRISE && Prefs.getAlert(mSettings, prayer) == type) names.add(getString(CONSTANT.TIME_NAMES[prayer]));
            }
            if (!names.isEmpty()) parts.add(getString(R.string.alert_group, getString(ALERT_NAMES[type]), TextUtils.join(getString(R.string.list_separator), names)));
        }
        return TextUtils.join(" · ", parts);
    }

    private String soundName(String key, int builtIn) {
        String uri = mSettings.getString(key, "");
        return uri.isEmpty() ? getString(builtIn) : PrayerAlertSheet.getDisplayName(requireContext(), Uri.parse(uri));
    }

    private void chooseSound(final String key) {
        mSoundKey = key;
        final boolean fajr = Prefs.FAJR_ADHAN_SOUND.equals(key);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(fajr ? R.string.fajr_adhan_sound : R.string.adhan_sound)
                .setItems(new CharSequence[]{getString(fajr ? R.string.sound_built_in_fajr : R.string.sound_built_in), getString(R.string.choose_sound)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            mSettings.edit().remove(key).apply();
                        } else {
                            mChooseSound.launch(new String[]{"audio/*"});
                        }
                    }
                })
                .show();
    }

    private void toggleTest() {
        if (mTestPlayer != null) {
            stopTest();
            return;
        }
        Context context = requireContext();
        Uri chosen = AdhanService.getChosenSound(mSettings, CONSTANT.DHUHR);
        MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(context, chosen != null ? chosen : Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.adhan));
            player.prepare();
        } catch (Exception ex) {
            player.release();
            Toast.makeText(context, R.string.sound_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopTest();
            }
        });
        player.start();
        mTestPlayer = player;
        refreshSummaries();
    }

    private void stopTest() {
        if (mTestPlayer == null) return;
        mTestPlayer.release();
        mTestPlayer = null;
        if (isAdded()) refreshSummaries();
    }

    private void showAdjustTimes() {
        final Context context = requireContext();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding / 2, padding / 2, 0);
        LayoutInflater inflater = getLayoutInflater();
        for (short i = CONSTANT.FAJR; i <= CONSTANT.ISHAA; i++) {
            final short prayer = i;
            final View row = inflater.inflate(R.layout.adjust_row, list, false);
            ((TextView) row.findViewById(R.id.adjust_name)).setText(CONSTANT.TIME_NAMES[i]);
            final TextView value = row.findViewById(R.id.adjust_value);
            value.setText(Formats.adjustment(context, Prefs.getOffset(mSettings, prayer)));
            View.OnClickListener step = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int offset = Math.max(-60, Math.min(60, Prefs.getOffset(mSettings, prayer) + (view.getId() == R.id.adjust_later ? 1 : -1)));
                    mSettings.edit().putInt(Prefs.OFFSET + prayer, offset).apply();
                    value.setText(Formats.adjustment(context, offset));
                }
            };
            row.findViewById(R.id.adjust_earlier).setOnClickListener(step);
            row.findViewById(R.id.adjust_later).setOnClickListener(step);
            list.addView(row);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.adjust_times)
                .setView(list)
                .setPositiveButton(R.string.done, null)
                .setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = mSettings.edit();
                        for (short prayer = CONSTANT.FAJR; prayer <= CONSTANT.ISHAA; prayer++) editor.remove(Prefs.OFFSET + prayer);
                        editor.apply();
                        App.broadcastPrayerTimeUpdate();
                    }
                })
                .show();
    }

    private int countIssues(Context context) {
        int issues = 0;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) issues++;
        if (!AlarmManagerCompat.canScheduleExactAlarms((AlarmManager) context.getSystemService(Context.ALARM_SERVICE))) issues++;
        if (((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_ALARM) == 0) issues++;
        return issues;
    }

    private void showAlarmCheck() {
        final Context context = requireContext();
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding / 2, padding / 2, 0);

        boolean notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();
        addCheck(list, R.string.check_notifications, notifications, R.string.check_allowed, R.string.check_notifications_off, new Runnable() {
            @Override
            public void run() {
                openSettings(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName())));
            }
        });
        boolean exact = AlarmManagerCompat.canScheduleExactAlarms((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        addCheck(list, R.string.check_exact, exact, R.string.check_allowed, R.string.check_exact_off, new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.getPackageName())));
                }
            }
        });
        boolean volume = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_ALARM) > 0;
        addCheck(list, R.string.check_volume, volume, R.string.check_volume_ok, R.string.check_volume_off, new Runnable() {
            @Override
            public void run() {
                openSettings(new Intent(Settings.ACTION_SOUND_SETTINGS));
            }
        });
        // Not a problem on most phones, so it's advice rather than a fault
        boolean battery = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
        // Advice rather than a problem: the alarms are set as alarm clocks, which battery saving leaves alone on most phones
        addCheck(list, R.string.check_battery, battery, R.string.check_battery_ok, R.string.check_battery_off, battery ? null : new Runnable() {
            @Override
            public void run() {
                openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.alarm_check)
                .setView(list)
                .setPositiveButton(R.string.done, null)
                .show();
    }

    private void addCheck(LinearLayout list, int title, boolean ok, int okStatus, int problemStatus, final Runnable fix) {
        boolean advice = title == R.string.check_battery;
        View row = getLayoutInflater().inflate(R.layout.check_row, list, false);
        ((TextView) row.findViewById(R.id.check_title)).setText(title);
        ((TextView) row.findViewById(R.id.check_status)).setText(ok ? okStatus : problemStatus);
        ImageView icon = row.findViewById(R.id.check_icon);
        icon.setImageResource(ok ? R.drawable.ic_check_circle : advice ? R.drawable.ic_info : R.drawable.ic_warning);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(list,
                ok ? androidx.appcompat.R.attr.colorPrimary : advice ? com.google.android.material.R.attr.colorOnSurfaceVariant : androidx.appcompat.R.attr.colorError)));
        if (fix != null && !ok) {
            View button = row.findViewById(R.id.check_fix);
            button.setVisibility(View.VISIBLE);
            ((TextView) button).setText(R.string.open_settings);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    fix.run();
                }
            });
        }
        list.addView(row);
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + requireContext().getPackageName())));
        }
    }

    /**
     * The language chosen for the app, or "" to follow the phone. Android 13+ is asked itself: AppCompat only
     * knows once an activity is running, and none is yet while Android rebuilds this screen in a new language.
     */
    private static String getAppLanguage(Context context) {
        LocaleListCompat locales = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? LocaleListCompat.wrap(context.getSystemService(LocaleManager.class).getApplicationLocales())
                : AppCompatDelegate.getApplicationLocales();
        return locales.isEmpty() ? "" : locales.get(0).getLanguage();
    }

    private static void fitPreferences(PreferenceGroup group) {
        // AndroidX leaves room for an icon beside every setting, which this screen doesn't use,
        // and cuts titles to one line, which longer languages need more than
        group.setIconSpaceReserved(false);
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            preference.setIconSpaceReserved(false);
            preference.setSingleLineTitle(false);
            if (preference instanceof PreferenceGroup) {
                fitPreferences((PreferenceGroup) preference);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The list scrolls clear of the navigation bar
        RecyclerView list = getListView();
        list.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(list, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, bars.bottom);
                return insets;
            }
        });
    }
}
