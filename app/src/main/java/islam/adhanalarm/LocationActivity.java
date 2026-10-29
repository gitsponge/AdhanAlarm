package islam.adhanalarm;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.util.Consumer;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import islam.adhanalarm.handler.CityDatabase;
import islam.adhanalarm.handler.LocationHandler;

/**
 * Sets the location from the phone, a city found in the bundled list, or typed coordinates. Nothing goes online.
 */
public class LocationActivity extends AppCompatActivity {

    private static final long LOCATION_TIMEOUT_MS = 20000;
    private static final int RESULTS = 40;

    private SharedPreferences mSettings;
    private LocationHandler mLocationHandler;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final CityAdapter mAdapter = new CityAdapter();
    private boolean mFinding;
    private String mQuery = "";
    private LinearProgressIndicator mSearchProgress;

    /** Only searches slow enough to notice show they're on their way */
    private static final long SEARCHING_DELAY_MS = 150;
    private final Runnable mShowSearching = new Runnable() {
        @Override
        public void run() {
            mSearchProgress.show();
        }
    };

    private final Runnable mLocationTimeout = new Runnable() {
        @Override
        public void run() {
            mFinding = false;
            showStatus(getString(R.string.location_unavailable));
        }
    };

    private final ActivityResultLauncher<String[]> mPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            if (LocationHandler.hasPermission(LocationActivity.this)) {
                findLocation();
            } else {
                showStatus(getString(R.string.location_permission_needed));
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        mSettings = App.getSettings(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        final RecyclerView results = findViewById(R.id.results);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.location_container), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });

        results.setLayoutManager(new LinearLayoutManager(this));
        results.setAdapter(mAdapter);

        mLocationHandler = new LocationHandler(this);
        mLocationHandler.addListener(new LocationHandler.LocationListener() {
            @Override
            public void onUpdated(Location location) {
                if (!mFinding) return;
                mFinding = false;
                mHandler.removeCallbacks(mLocationTimeout);
                mSettings.edit().putBoolean(Prefs.AUTO_LOCATION, true).apply();
                Locations.save(LocationActivity.this, location.getLatitude(), location.getLongitude(), new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });

        findViewById(R.id.use_current_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (LocationHandler.hasPermission(LocationActivity.this)) {
                    findLocation();
                } else {
                    mPermissionRequest.launch(LocationHandler.PERMISSIONS);
                }
            }
        });
        findViewById(R.id.enter_coordinates).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterCoordinates();
            }
        });

        TextInputEditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                search(s.toString());
            }
        });

        showCurrent();
        mSearchProgress = findViewById(R.id.search_progress);
        mSearchProgress.setVisibilityAfterHide(View.INVISIBLE); // Keeps its place, so the results don't jump
        CityDatabase.prepare(this); // So the first search is as quick as the rest
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showCurrent() {
        String name = Prefs.getPlaceName(mSettings);
        ((TextView) findViewById(R.id.current_place)).setText(name.isEmpty() ? getString(R.string.location_unnamed) : name);
        ((TextView) findViewById(R.id.current_coordinates)).setText(Formats.savedCoordinates(this, mSettings));
        ((TextView) findViewById(R.id.current_note)).setText(Formats.placeNote(this, mSettings));
    }

    private void showStatus(String status) {
        TextView view = findViewById(R.id.location_status);
        view.setText(status);
        view.setVisibility(View.VISIBLE);
    }

    private void findLocation() {
        if (!LocationHandler.isEnabled(this)) {
            showStatus(getString(R.string.location_off));
            return;
        }
        mFinding = true;
        showStatus(getString(R.string.finding_location));
        mHandler.removeCallbacks(mLocationTimeout);
        mHandler.postDelayed(mLocationTimeout, LOCATION_TIMEOUT_MS);
        mLocationHandler.update(true);
    }

    private void search(final String query) {
        mQuery = query;
        boolean searching = !query.trim().isEmpty();
        findViewById(R.id.current_card).setVisibility(searching ? View.GONE : View.VISIBLE);
        findViewById(R.id.use_current_location).setVisibility(searching ? View.GONE : View.VISIBLE);
        TextView status = findViewById(R.id.location_status);
        status.setVisibility(searching || status.length() == 0 ? View.GONE : View.VISIBLE);
        if (!searching) findViewById(R.id.enter_coordinates).setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mShowSearching);
        mHandler.postDelayed(mShowSearching, SEARCHING_DELAY_MS);
        CityDatabase.search(this, query, RESULTS, new Consumer<List<CityDatabase.City>>() {
            @Override
            public void accept(List<CityDatabase.City> cities) {
                if (!query.equals(mQuery)) return; // A newer search is on its way
                mHandler.removeCallbacks(mShowSearching);
                mSearchProgress.hide();
                mAdapter.setCities(cities);
                findViewById(R.id.no_results).setVisibility(cities.isEmpty() && CityDatabase.isSearchable(query) ? View.VISIBLE : View.GONE);
                // Typing coordinates is the way out when no city matches, and otherwise only takes room from the results
                findViewById(R.id.enter_coordinates).setVisibility(cities.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void choose(CityDatabase.City city) {
        mSettings.edit().putBoolean(Prefs.AUTO_LOCATION, false).apply();
        Locations.save(this, city);
        Toast.makeText(this, R.string.auto_location_off, Toast.LENGTH_LONG).show();
        finish();
    }

    private void enterCoordinates() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_coordinates, null);
        final TextInputLayout latitudeLayout = view.findViewById(R.id.latitude_layout);
        final TextInputLayout longitudeLayout = view.findViewById(R.id.longitude_layout);
        final TextInputEditText latitude = view.findViewById(R.id.latitude);
        final TextInputEditText longitude = view.findViewById(R.id.longitude);
        latitude.setText(mSettings.getString(Prefs.LATITUDE, ""));
        longitude.setText(mSettings.getString(Prefs.LONGITUDE, ""));
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.enter_coordinates)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();
        // Checked before the dialog closes, so a mistake can be corrected in place
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double lat = parse(latitude.getText(), 90);
                Double lon = parse(longitude.getText(), 180);
                latitudeLayout.setError(lat == null ? getString(R.string.latitude_range) : null);
                longitudeLayout.setError(lon == null ? getString(R.string.longitude_range) : null);
                if (lat == null || lon == null) return;
                mSettings.edit().putBoolean(Prefs.AUTO_LOCATION, false).apply();
                Locations.save(LocationActivity.this, lat, lon, null);
                dialog.dismiss();
                Toast.makeText(LocationActivity.this, R.string.auto_location_off, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private static Double parse(CharSequence text, double limit) {
        String plain = Formats.plainNumber(String.valueOf(text));
        if (plain == null) return null;
        double value = Double.parseDouble(plain);
        return Math.abs(value) <= limit ? value : null;
    }

    private class CityAdapter extends RecyclerView.Adapter<CityAdapter.Holder> {
        private final List<CityDatabase.City> mCities = new ArrayList<>();

        void setCities(List<CityDatabase.City> cities) {
            mCities.clear();
            mCities.addAll(cities);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.city_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final CityDatabase.City city = mCities.get(position);
            holder.name.setText(city.name);
            holder.place.setText(city.getPlace());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    choose(city);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mCities.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, place;

            Holder(View view) {
                super(view);
                name = view.findViewById(R.id.city_name);
                place = view.findViewById(R.id.city_place);
            }
        }
    }
}
