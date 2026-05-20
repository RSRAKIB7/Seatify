package com.android.seatify

import android.app.Application
import utils.SupabaseClient

class SmartHostelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClient.init(this)
    }
}
