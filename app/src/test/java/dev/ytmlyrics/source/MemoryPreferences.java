package dev.ytmlyrics.source;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Test implementation of the persistence interface; no Android process is required. */
final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> data = new HashMap<>();
    public Map<String, ?> getAll() { return Map.copyOf(data); }
    public String getString(String k, String d) { return (String) data.getOrDefault(k, d); }
    @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { return (Set<String>) data.getOrDefault(k, d); }
    public int getInt(String k, int d) { return (Integer) data.getOrDefault(k, d); }
    public long getLong(String k, long d) { return (Long) data.getOrDefault(k, d); }
    public float getFloat(String k, float d) { return (Float) data.getOrDefault(k, d); }
    public boolean getBoolean(String k, boolean d) { return (Boolean) data.getOrDefault(k, d); }
    public boolean contains(String k) { return data.containsKey(k); }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    public Editor edit() {
        return new Editor() {
            private final Map<String, Object> pending = new HashMap<>();
            private final Set<String> removed = new HashSet<>();
            private boolean clear;
            public Editor putString(String k, String v) { pending.put(k, v); return this; }
            public Editor putStringSet(String k, Set<String> v) { pending.put(k, Set.copyOf(v)); return this; }
            public Editor putInt(String k, int v) { pending.put(k, v); return this; }
            public Editor putLong(String k, long v) { pending.put(k, v); return this; }
            public Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            public Editor remove(String k) { removed.add(k); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() { apply(); return true; }
            public void apply() { if (clear) data.clear(); removed.forEach(data::remove); data.putAll(pending); }
        };
    }
}
