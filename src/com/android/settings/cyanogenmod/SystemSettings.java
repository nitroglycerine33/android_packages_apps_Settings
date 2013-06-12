/*
 * Copyright (C) 2012 The CyanogenMod project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.cyanogenmod;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class SystemSettings extends SettingsPreferenceFragment
			implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "SystemSettings";

    private static final String KEY_DUAL_PANE = "dual_pane";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_BATTERY_LIGHT = "battery_light";
    private static final String KEY_LOCK_CLOCK = "lock_clock";
    private static final String PREF_POWER_CRT_SCREEN_ON = "system_power_crt_screen_on";
    private static final String PREF_POWER_CRT_SCREEN_OFF = "system_power_crt_screen_off";

    private PreferenceScreen mNotificationPulse;
    private PreferenceScreen mBatteryPulse;

    CheckBoxPreference mDualPane;
    CheckBoxPreference mCrtOff;
    CheckBoxPreference mCrtOn;

    Context mContext;

    private boolean mIsPrimary;
    private boolean isCrtOffChecked = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	mContext = getActivity();

        addPreferencesFromResource(R.xml.system_settings);
        PreferenceScreen prefScreen = getPreferenceScreen();

       // respect device default configuration
        // true fades while false animates
        boolean electronBeamFadesConfig = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_animateScreenLights);

        // use this to enable/disable crt on feature
        // crt only works if crt off is enabled
        // total system failure if only crt on is enabled
        isCrtOffChecked = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SYSTEM_POWER_ENABLE_CRT_OFF,
                electronBeamFadesConfig ? 0 : 1) == 1;

        mCrtOff = (CheckBoxPreference) findPreference(PREF_POWER_CRT_SCREEN_OFF);
        mCrtOff.setChecked(isCrtOffChecked);
        mCrtOff.setOnPreferenceChangeListener(this);

        mCrtOn = (CheckBoxPreference) findPreference(PREF_POWER_CRT_SCREEN_ON);
        mCrtOn.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SYSTEM_POWER_ENABLE_CRT_ON, 0) == 1);
        mCrtOn.setEnabled(isCrtOffChecked);
        mCrtOn.setOnPreferenceChangeListener(this);

        mDualPane = (CheckBoxPreference) findPreference(KEY_DUAL_PANE);
        boolean preferDualPane = getResources().getBoolean(
                com.android.internal.R.bool.preferences_prefer_dual_pane);
        boolean dualPaneMode = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.DUAL_PANE_PREFS, (preferDualPane ? 1 : 0)) == 1;
        mDualPane.setChecked(dualPaneMode);

        // Determine which user is logged in
        mIsPrimary = UserHandle.myUserId() == UserHandle.USER_OWNER;
        if (mIsPrimary) {
            // Primary user only preferences
            // Battery lights
            mBatteryPulse = (PreferenceScreen) findPreference(KEY_BATTERY_LIGHT);
            if (mBatteryPulse != null) {
                if (getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveBatteryLed) == false) {
                    prefScreen.removePreference(mBatteryPulse);
                    mBatteryPulse = null;
                }
            }
        } else {
            // Secondary user is logged in, remove all primary user specific preferences
            prefScreen.removePreference(findPreference(KEY_BATTERY_LIGHT));
        }

        // Preferences that applies to all users
        // Notification lights
        mNotificationPulse = (PreferenceScreen) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null) {
            if (!getResources().getBoolean(com.android.internal.R.bool.config_intrusiveNotificationLed)) {
                prefScreen.removePreference(mNotificationPulse);
                mNotificationPulse = null;
            }
        }

        // Don't display the lock clock preference if its not installed
        removePreferenceIfPackageNotInstalled(findPreference(KEY_LOCK_CLOCK));
    }

    @Override
    public void onResume() {
        super.onResume();

        // All users
        if (mNotificationPulse != null) {
            updateLightPulseDescription();
        }

        // Primary user only
        if (mIsPrimary && mBatteryPulse != null) {
            updateBatteryPulseDescription();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void updateLightPulseDescription() {
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_PULSE, 0) == 1) {
            mNotificationPulse.setSummary(getString(R.string.notification_light_enabled));
        } else {
            mNotificationPulse.setSummary(getString(R.string.notification_light_disabled));
        }
    }

    private void updateBatteryPulseDescription() {
        if (Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.BATTERY_LIGHT_ENABLED, 1) == 1) {
            mBatteryPulse.setSummary(getString(R.string.notification_light_enabled));
        } else {
            mBatteryPulse.setSummary(getString(R.string.notification_light_disabled));
        }
     }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mDualPane) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DUAL_PANE_PREFS,
                    ((CheckBoxPreference) preference).isChecked() ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
         if (mCrtOff.equals(preference)) {
            isCrtOffChecked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SYSTEM_POWER_ENABLE_CRT_OFF,
                    (isCrtOffChecked ? 1 : 0));
            // if crt off gets turned off, crt on gets turned off and disabled
            if (!isCrtOffChecked) {
                Settings.System.putInt(getActivity().getContentResolver(),
                        Settings.System.SYSTEM_POWER_ENABLE_CRT_ON, 0);
                mCrtOn.setChecked(false);
            }
            mCrtOn.setEnabled(isCrtOffChecked);
            return true;
        } else if (mCrtOn.equals(preference)) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SYSTEM_POWER_ENABLE_CRT_ON,
                    ((Boolean) newValue).booleanValue() ? 1 : 0);
            return true;
        }
        return false;
    }

}
