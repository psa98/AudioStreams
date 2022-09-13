package c.ponom.audiostreams

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream

class App : Application(){

    override fun onCreate() {
        super.onCreate()
        appContext=this
        val a=getAssetStream("test_60sec_440sinewave.ac3")


    }

    fun getAssetStream(filename: String): InputStream? {
        val assets: AssetManager = appContext.assets

        val inputStream: InputStream? = try {
            assets.open( filename)

        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
        return inputStream
    }

    companion object{
        lateinit var appContext:Context
        private set
    }
}