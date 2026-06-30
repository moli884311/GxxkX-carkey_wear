package com.wuling.keyless

import android.app.Application
import com.wuling.keyless.service.LogRepository

class WulingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogRepository.init(this)
    }
}
