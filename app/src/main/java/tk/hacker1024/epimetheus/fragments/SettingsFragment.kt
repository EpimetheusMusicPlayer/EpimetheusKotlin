package tk.hacker1024.epimetheus.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import tk.hacker1024.epimetheus.MainActivity
import tk.hacker1024.epimetheus.R

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)
        PreferenceManager.setDefaultValues(activity, R.xml.preferences, false)

        findPreference("logout").setOnPreferenceClickListener {
            (activity as MainActivity).logout()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "use_portaller" -> findPreference("use_portaller").summary = "Requires app restart/sign out for changes to take effect."
        }
    }
}
