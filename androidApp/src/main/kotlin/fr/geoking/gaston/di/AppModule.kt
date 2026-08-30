package fr.geoking.gaston.di

import android.content.Context
import fr.geoking.gaston.feature.network.AndroidNetworkService
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.feature.weather.AndroidWeatherLookup
import fr.geoking.gaston.feature.permission.AndroidPermissionManager
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.weather.WeatherLookup
import fr.geoking.gaston.shared.platform.PermissionManager
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.ui.dashboard.PhoneDashboardViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fr.geoking.gaston.feature.settings.FirestoreSettingsSync
import androidx.room.Room
import fr.geoking.gaston.persistence.AppDatabase
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.geocoding.NominatimGeocodingClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import fr.geoking.gaston.shared.network.RateLimitPlugin
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.toMap
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.NetworkLog
import fr.geoking.gaston.premium.BillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlin.random.Random
import fr.geoking.gaston.diagnostics.DiagnosticsPersistence

val appModule = module {
    single<HttpClient> {
        val settingsManager = get<SettingsManager>()
        HttpClient(OkHttp) {
            val requestBodyKey = AttributeKey<String>("DebugRequestBody")

            install(ResponseObserver) {
                onResponse { response ->
                    if (settingsManager.settings.value.debugLoggingEnabled) {
                        val request = response.request
                        val reqBody = request.attributes.getOrNull(requestBodyKey)
                        // Never call bodyAsText() here — Crashlytics OOM on ~79MB OCPI/catalog
                        // payloads (AppModuleKt.invokeSuspend → HttpResponse.bodyAsText).
                        val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                        val respBody = if (contentLength != null) {
                            "[body omitted: ${contentLength} bytes]"
                        } else {
                            "[body omitted]"
                        }

                        DebugLogStore.addLog(
                            NetworkLog(
                                id = UUID.randomUUID().toString(),
                                url = request.url.toString(),
                                host = request.url.host,
                                method = request.method.value,
                                requestHeaders = request.headers.toMap(),
                                requestBody = truncateDebugBody(reqBody),
                                responseHeaders = response.headers.toMap(),
                                responseBody = respBody,
                                statusCode = response.status.value,
                                durationMs = response.responseTime.timestamp - response.requestTime.timestamp,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            install(createClientPlugin("NetworkDebugLog") {
                on(io.ktor.client.plugins.api.Send) { request ->
                    if (settingsManager.settings.value.debugLoggingEnabled) {
                        val content = request.body
                        if (content is io.ktor.http.content.TextContent) {
                            request.attributes.put(requestBodyKey, content.text)
                        } else if (content is io.ktor.client.utils.EmptyContent) {
                            request.attributes.put(requestBodyKey, "")
                        }
                    }
                    proceed(request)
                }
            })

            install(RateLimitPlugin)

            install(HttpRequestRetry) {
                maxRetries = 2

                retryIf { request, response ->
                    val method = request.method
                    val idempotent =
                        method == HttpMethod.Get ||
                            method == HttpMethod.Head ||
                            method == HttpMethod.Options

                    if (!idempotent) return@retryIf false

                    val status = response.status
                    status.value in 500..599
                }

                retryOnExceptionIf { _, cause ->
                    when (cause) {
                        is SocketTimeoutException -> true
                        is HttpRequestTimeoutException -> true
                        is ConnectException -> true
                        is UnknownHostException -> true
                        is IOException -> {
                            val msg = cause.message?.lowercase() ?: ""
                            msg.contains("connection reset") ||
                                msg.contains("broken pipe") ||
                                msg.contains("software caused connection abort") ||
                                msg.contains("unexpected end of stream")
                        }
                        else -> false
                    }
                }

                delayMillis { retry ->
                    val base = 300L
                    val max = 3_000L
                    val exp = (base shl retry.coerceAtMost(10)).coerceAtMost(max)
                    val jitter = (exp * (0.15 + Random.nextDouble() * 0.25)).toLong()
                    exp + jitter
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
    }

    // Used by the phone dashboard "Where to?" autocomplete even before map deps load.
    single<GeocodingClient> { NominatimGeocodingClient(get()) }

    // Koin singletons can't be null; keep Firebase deps optional by resolving them safely here.
    single {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        FirestoreSettingsSync(firestore = firestore, firebaseAuth = auth)
    }
    single<SettingsManager> { SettingsManager(androidContext(), getOrNull()) }

    single { BillingManager().also { it.attachCustomerInfoListener() } }

    single<DiagnosticStore> { DiagnosticStore() }

    // Persist error log for later retrieval & copy from Settings.
    single(createdAtStart = true) { DiagnosticsPersistence(androidContext(), get()) }

    single<GoogleAuthManager> {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        GoogleAuthManager(androidContext(), get(), get(), auth)
    }

    single<PermissionManager> {
        AndroidPermissionManager(androidContext())
    }

    single { NotificationHelper(androidContext()) }

    single { fr.geoking.gaston.repository.StationPriceHistoryRepository(dao = get<AppDatabase>().stationPriceSampleDao(), nationalDao = get<AppDatabase>().nationalFuelPriceDao()) }

    single<WeatherLookup> {
        AndroidWeatherLookup(androidContext(), get())
    }

    single<NetworkService> {
        AndroidNetworkService(
            androidContext(),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            get(),
            get()
        )
    }

    // Initialize ConnectivityManager here so it starts at app launch
    single(createdAtStart = true) {
        val notificationHelper = get<NotificationHelper>()
        ConnectivityManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            networkService = get(),
            networkSettings = get<SettingsManager>()
        ).also { manager ->
            // Observe border crossing events to show notifications
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                manager.borderCrossingEvents.collect { countryName ->
                    notificationHelper.showBorderCrossingNotification(countryName)
                }
            }
        }
    }

    single { FuelForecastRepository(http = get(), db = get()) }

    viewModel {
        PhoneDashboardViewModel(
            settingsManager = get(),
            context = androidContext()
        )
    }

    single<AppDatabase> {
        fun buildAndValidate(builder: androidx.room.RoomDatabase.Builder<AppDatabase>): AppDatabase? {
            return try {
                val db = builder.fallbackToDestructiveMigration(dropAllTables = true).build()
                db.openHelper.writableDatabase.query("SELECT 1").close()
                db
            } catch (e: Throwable) {
                android.util.Log.e("AppModule", "Database build/validation failed", e)
                null
            }
        }

        android.util.Log.d("AppModule", "Building persistent Room database...")
        val persistentDb = buildAndValidate(
            Room.databaseBuilder(androidContext(), AppDatabase::class.java, "gaston-db")
        )

        if (persistentDb != null) {
            persistentDb
        } else {
            android.util.Log.w("AppModule", "Persistent DB failed. Falling back to in-memory.")
            buildAndValidate(
                Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
            ) ?: throw IllegalStateException("Both persistent and in-memory databases failed to initialize")
        }
    }
}

private const val DEBUG_BODY_MAX_CHARS = 8_192

/** Cap debug request payloads so oversized POSTs cannot OOM the log store. */
private fun truncateDebugBody(body: String?, maxChars: Int = DEBUG_BODY_MAX_CHARS): String? {
    if (body == null) return null
    if (body.length <= maxChars) return body
    return body.take(maxChars) + "…[truncated ${body.length - maxChars} chars]"
}
