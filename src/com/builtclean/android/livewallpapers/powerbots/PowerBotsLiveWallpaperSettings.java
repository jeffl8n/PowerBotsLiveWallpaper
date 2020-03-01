package com.builtclean.android.livewallpapers.powerbots;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class PowerBotsLiveWallpaperSettings extends PreferenceActivity
		implements ColorPickerDialog.OnColorChangedListener,
		SharedPreferences.OnSharedPreferenceChangeListener {
	public static final String BACKGROUND_COLOR_PREFERENCE_KEY = "background_color";
	public static final int BACKGROUND_COLOR_DEFAULT = Color.BLACK;
	public static final String ROBOT_COLOR_PREFERENCE_KEY = "robot_color";
	public static final int ROBOT_COLOR_DEFAULT = Color.GREEN;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		getPreferenceManager().setSharedPreferencesName(
				PowerBotsLiveWallpaper.SHARED_PREFS_NAME);
		addPreferencesFromResource(R.xml.wallpaper_settings);
		getPreferenceManager().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		getPreferenceManager().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
	}

	@Override
	public void colorChanged(String preferenceKey, int color) {
		getPreferenceManager().getSharedPreferences().edit()
				.putInt(preferenceKey, color).commit();
	}

	public boolean onPreferenceClick(Preference preference) {

		if (preference.getKey().equals(ROBOT_COLOR_PREFERENCE_KEY)) {
			new ColorPickerDialog(this, this, ROBOT_COLOR_PREFERENCE_KEY,
					getPreferenceManager().getSharedPreferences().getInt(
							ROBOT_COLOR_PREFERENCE_KEY, ROBOT_COLOR_DEFAULT),
					ROBOT_COLOR_DEFAULT, "Pick a new Robot color").show();
		} else if (preference.getKey().equals(BACKGROUND_COLOR_PREFERENCE_KEY)) {
			new ColorPickerDialog(this, this, BACKGROUND_COLOR_PREFERENCE_KEY,
					getPreferenceManager().getSharedPreferences().getInt(
							BACKGROUND_COLOR_PREFERENCE_KEY,
							BACKGROUND_COLOR_DEFAULT),
					BACKGROUND_COLOR_DEFAULT, "Pick a new Background color")
					.show();
		}
		return true;
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {
		return onPreferenceClick(preference);
	}

}
