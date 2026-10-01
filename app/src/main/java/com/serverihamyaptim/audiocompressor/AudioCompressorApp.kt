package com.serverihamyaptim.audiocompressor

import android.app.Application
import com.serverihamyaptim.audiocompressor.data.HistoryDatabase

class AudioCompressorApp : Application() {
    val database by lazy { HistoryDatabase.get(this) }
}
