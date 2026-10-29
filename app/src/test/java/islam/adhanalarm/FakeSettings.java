package islam.adhanalarm;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Settings held in memory, for tests
 */
public class FakeSettings implements SharedPreferences {
    private final Map<String, Object> mValues = new HashMap<>();

    public FakeSettings(Object... keysAndValues) {
        for (int i = 0; i < keysAndValues.length; i += 2) {
            mValues.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
    }

    @Override public Map<String, ?> getAll() { return mValues; }
    @Override public String getString(String key, String defValue) { return mValues.containsKey(key) ? (String) mValues.get(key) : defValue; }
    @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
    @Override public int getInt(String key, int defValue) { return mValues.containsKey(key) ? (Integer) mValues.get(key) : defValue; }
    @Override public long getLong(String key, long defValue) { return defValue; }
    @Override public float getFloat(String key, float defValue) { return defValue; }
    @Override public boolean getBoolean(String key, boolean defValue) { return mValues.containsKey(key) ? (Boolean) mValues.get(key) : defValue; }
    @Override public boolean contains(String key) { return mValues.containsKey(key); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

    @Override
    public Editor edit() {
        return new Editor() {
            @Override public Editor putString(String key, String value) { mValues.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { mValues.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { return this; }
            @Override public Editor putFloat(String key, float value) { return this; }
            @Override public Editor putBoolean(String key, boolean value) { mValues.put(key, value); return this; }
            @Override public Editor remove(String key) { mValues.remove(key); return this; }
            @Override public Editor clear() { mValues.clear(); return this; }
            @Override public boolean commit() { return true; }
            @Override public void apply() { }
        };
    }
}
