package com.ichi2.anki;

import android.preference.PreferenceScreen;

public interface PreferenceContext {
    void addPreferencesFromResource(int preferencesResId);
    PreferenceScreen getPreferenceScreen();
}
