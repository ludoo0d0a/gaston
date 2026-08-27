package fr.geoking.gaston

import android.app.Application
import com.google.android.gms.ads.MobileAds
import fr.geoking.gaston.di.appModule
import fr.geoking.gaston.premium.RevenueCatInitializer
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

import fr.geoking.gaston.diagnostics.DiagnosticsPersistence

class GastonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
        android.util.Log.d("GastonApplication", "onCreate start")
        try {
            // Safe to call once; uses test ids by default unless overridden by local.properties/env
            MobileAds.initialize(this)
            RevenueCatInitializer.initialize(this, BuildConfig.REVENUECAT_API_KEY)
            startKoin {
                androidContext(this@GastonApplication)
                modules(appModule)
            }
            android.util.Log.d("GastonApplication", "Koin started OK")
        } catch (e: Throwable) {
            initError = e
            android.util.Log.e("GastonApplication", "Koin/DI init failed", e)
        }

        if (BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
            try {
                MobileAds.initialize(this) {}
            } catch (e: Throwable) {
                android.util.Log.e("GastonApplication", "AdMob init failed", e)
            }
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("GastonApplication", "Uncaught exception in thread ${thread.name}", throwable)
                DiagnosticsPersistence.persistCrash(this, throwable)
            } catch (e: Throwable) {
                android.util.Log.e("GastonApplication", "Failed to log uncaught exception", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        /** Set when startKoin or module init fails; MainActivity shows this instead of crashing. */
        @Volatile
        var initError: Throwable? = null
            private set
    }
}
