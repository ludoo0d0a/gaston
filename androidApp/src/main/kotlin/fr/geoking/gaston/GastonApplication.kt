package fr.geoking.gaston

import android.app.Application
import fr.geoking.gaston.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GastonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("GastonApplication", "onCreate start")
        try {
            startKoin {
                androidContext(this@GastonApplication)
                modules(appModule)
            }
            android.util.Log.d("GastonApplication", "Koin started OK")
        } catch (e: Throwable) {
            initError = e
            android.util.Log.e("GastonApplication", "Koin/DI init failed", e)
        }
    }

    companion object {
        /** Set when startKoin or module init fails; MainActivity shows this instead of crashing. */
        @Volatile
        var initError: Throwable? = null
            private set
    }
}
