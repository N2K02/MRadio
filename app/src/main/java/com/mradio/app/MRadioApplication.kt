package com.mradio.app

import android.app.Application

class MRadioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        RadioAlarm.init(this)
        SoundFx.init(this)
    }
}
