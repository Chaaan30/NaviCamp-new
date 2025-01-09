package com.capstone.navicamp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class ForceTheme : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}