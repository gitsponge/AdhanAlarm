package islam.adhanalarm;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        // The toolbar clears the status bar and the sides; the list scrolls clear of the navigation bar itself
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_container), new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View view, @NonNull WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, 0);
                return insets;
            }
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.settings_content, new SettingsFragment()).commit();
        }
    }
}
