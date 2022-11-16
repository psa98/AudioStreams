package c.ponom.audiostreamsdemo

import android.app.Application
import android.content.Context

const val TAG = "AudioStreamsDemo"
class App : Application(){

    override fun onCreate() {
        super.onCreate()
        appContext=this

    }

    companion object{
        lateinit var appContext:Context
        private set
    }
}