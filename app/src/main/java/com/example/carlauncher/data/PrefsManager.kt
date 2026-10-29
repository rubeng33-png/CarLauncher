package com.example.carlauncher.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("car_launcher_prefs", Context.MODE_PRIVATE)

    var vehiclePhotoUri: String?
        get() = prefs.getString("vehicle_photo_uri", null)
        set(v) = prefs.edit().putString("vehicle_photo_uri", v).apply()

    var currentOdometer: Int
        get() = prefs.getInt("current_odometer", 0)
        set(v) = prefs.edit().putInt("current_odometer", v).apply()

    var engineIntervalKm: Int
        get() = prefs.getInt("engine_interval", 10000)
        set(v) = prefs.edit().putInt("engine_interval", v).apply()

    var engineLastChangeKm: Int
        get() = prefs.getInt("engine_last", 0)
        set(v) = prefs.edit().putInt("engine_last", v).apply()

    var gearboxIntervalKm: Int
        get() = prefs.getInt("gearbox_interval", 60000)
        set(v) = prefs.edit().putInt("gearbox_interval", v).apply()

    var gearboxLastChangeKm: Int
        get() = prefs.getInt("gearbox_last", 0)
        set(v) = prefs.edit().putInt("gearbox_last", v).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", true)
        set(v) = prefs.edit().putBoolean("is_dark_mode", v).apply()

    var userName: String
        get() = prefs.getString("user_name", "conductor") ?: "conductor"
        set(v) = prefs.edit().putString("user_name", v).apply()
}
