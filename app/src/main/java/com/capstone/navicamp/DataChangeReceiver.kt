package com.capstone.navicamp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DataChangeReceiver() : BroadcastReceiver() {
    private var onDataChanged: (() -> Unit)? = null

    constructor(onDataChanged: () -> Unit) : this() {
        this.onDataChanged = onDataChanged
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        onDataChanged?.invoke()
    }
}