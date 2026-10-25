package islam.adhanalarm;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;

import islam.adhanalarm.handler.LocationHandler;

public class SettingsFragment extends PreferenceFragmentCompat {
    // The keyboard for each text entry. AndroidX ignores android:inputType in settings.xml, so it's set here.
    static final Map<String, Integer> TEXT_ENTRIES = new HashMap<>();
    static {
        final int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        TEXT_ENTRIES.put("latitude", decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TEXT_ENTRIES.put("longitude", decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TEXT_ENTRIES.put("altitude", decimal);
        TEXT_ENTRIES.put("pressure", decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TEXT_ENTRIES.put("temperature", decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TEXT_ENTRIES.put("offsetMinutes", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
    }
    LocationHandler mLocationHandler;

    private final ActivityResultLauncher<String[]> mLocationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            if (LocationHandler.hasPermission(requireContext())) {
                mLocationHandler.update();
            }
        }
    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        removeIconSpace(getPreferenceScreen());

        for (Map.Entry<String, Integer> textEntry : TEXT_ENTRIES.entrySet()) {
            final int inputType = textEntry.getValue();
            EditTextPreference preference = findPreference(textEntry.getKey());
            preference.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            preference.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(inputType);
                }
            });
        }

        mLocationHandler = new LocationHandler(requireContext());
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location currentLocation) {
                // Setting the text saves it and refreshes the screen
                ((EditTextPreference) findPreference("latitude")).setText(Double.toString(currentLocation.getLatitude()));
                ((EditTextPreference) findPreference("longitude")).setText(Double.toString(currentLocation.getLongitude()));
            }
        });

        findPreference("lookupGPS").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(@NonNull Preference preference) {
                if (LocationHandler.hasPermission(requireContext())) {
                    mLocationHandler.update();
                } else {
                    mLocationPermissionRequest.launch(LocationHandler.PERMISSIONS);
                }
                return true;
            }
        });

        findPreference("privacyPolicy").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(@NonNull Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://cantangroup.com/adhan-alarm/privacy/")));
                return true;
            }
        });

        findPreference("information").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(@NonNull Preference preference) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gitsponge/AdhanAlarm")));
                return true;
            }
        });

        findPreference("information").setSummary(getText(R.string.information_text).toString().replace("#", BuildConfig.VERSION_NAME));
    }

    private static void removeIconSpace(PreferenceGroup group) {
        // AndroidX leaves room for an icon beside every setting, which this screen doesn't use
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            preference.setIconSpaceReserved(false);
            if (preference instanceof PreferenceGroup) {
                removeIconSpace((PreferenceGroup) preference);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Android 15+ draws the app behind the system bars and the action bar (the top inset includes it), so let the list scroll clear of them
        RecyclerView list = getListView();
        list.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(list, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });
    }
}
