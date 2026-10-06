package fr.geoking.gaston.di

import android.content.Context
import fr.geoking.gaston.feature.network.AndroidNetworkService
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.feature.notification.InAppNotificationCenter
import fr.geoking.gaston.feature.weather.AndroidWeatherLookup
import fr.geoking.gaston.feature.permission.AndroidPermissionManager
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.R
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.location.ConnectivityManager
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.weather.WeatherLookup
import fr.geoking.gaston.shared.platform.PermissionManager
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.toll.VignetteService
import fr.geoking.gaston.feature.accident.AccidentProfileStore
import fr.geoking.gaston.feature.maintenance.MaintenanceReminderNotifier
import fr.geoking.gaston.feature.maintenance.MaintenanceRepository
import fr.geoking.gaston.ui.accident.AccidentViewModel
import fr.geoking.gaston.ui.dashboard.PhoneDashboardViewModel
import fr.geoking.gaston.ui.maintenance.MaintenanceViewModel
import org.koin.core.module.dsl.viewModel
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
import io.ktor.client.statement.bodyAsText
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
                    if (settingsManager.settings.value.debugLoggingEnabled || settingsManager.settings.value.debugBarEnabled) {
                        val request = response.request
                        val reqBody = request.attributes.getOrNull(requestBodyKey)
                        val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                        val logId = UUID.randomUUID().toString()

                        // Always keep a truncated preview (never omit). Truncation avoids
                        // OOM in the log store / debug-bar Compose viewer.
                        val (respBody, bodyLength) = try {
                            val bodyText = response.bodyAsText()
                            truncateDebugBody(bodyText) to bodyText.length.toLong()
                        } catch (e: Throwable) {
                            "[body unreadable: ${e.message}]" to 0L
                        }
                        val responseSizeBytes = contentLength ?: bodyLength

                        val truncatedReq = truncateDebugBody(reqBody)
                        if (!truncatedReq.isNullOrBlank()) {
                            fr.geoking.gaston.shared.logging.DebugLogPayloadCache.store(
                                logId,
                                isRequest = true,
                                body = truncatedReq,
                            )
                        }
                        if (!respBody.isNullOrBlank()) {
                            fr.geoking.gaston.shared.logging.DebugLogPayloadCache.store(
                                logId,
                                isRequest = false,
                                body = respBody,
                            )
                        }

                        DebugLogStore.addLog(
                            NetworkLog(
                                id = logId,
                                url = request.url.toString(),
                                host = request.url.host,
                                method = request.method.value,
                                requestHeaders = request.headers.toMap(),
                                requestBody = truncatedReq,
                                responseHeaders = response.headers.toMap(),
                                responseBody = respBody,
                                statusCode = response.status.value,
                                durationMs = response.responseTime.timestamp - response.requestTime.timestamp,
                                timestamp = System.currentTimeMillis(),
                                requestSizeBytes = reqBody?.length?.toLong() ?: 0L,
                                responseSizeBytes = responseSizeBytes
                            )
                        )
                    }
                }
            }

            install(createClientPlugin("NetworkDebugLog") {
                on(io.ktor.client.plugins.api.Send) { request ->
                    if (settingsManager.settings.value.debugLoggingEnabled || settingsManager.settings.value.debugBarEnabled) {
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

    single { fr.geoking.gaston.radar.DangerZoneAlertTester() }

    single { fr.geoking.gaston.radar.DangerZoneHudStore() }

    single { fr.geoking.gaston.parked.ParkCandidateStore(androidContext()) }

    single {
        fr.geoking.gaston.parked.ParkWalkAwayMonitor(
            context = androidContext(),
            settingsManager = get(),
            notificationHelper = get(),
            candidateStore = get(),
        )
    }

    single {
        fr.geoking.gaston.parked.AaPostSessionParkSuggester(
            context = androidContext(),
            settingsManager = get(),
            notificationHelper = get(),
            candidateStore = get(),
            walkAwayMonitor = get(),
        )
    }

    single { InAppNotificationCenter() }

    single {
        val context = androidContext()
        val notificationHelper = get<NotificationHelper>()
        fr.geoking.tools.inappupdate.InAppUpdateHelper(
            context = context,
            notificationSpec = fr.geoking.tools.inappupdate.UpdateNotificationSpec(
                channelId = NotificationHelper.CHANNEL_ID,
                channelName = context.getString(R.string.dashboard_network),
                smallIcon = R.drawable.ic_notifications,
                title = context.getString(R.string.update_available_title),
                message = context.getString(R.string.update_available_message),
                launchActivityClass = fr.geoking.gaston.MainActivity::class.java,
            ),
            onUpdateAvailableExtra = { notificationHelper.showUpdateAvailableCarNotification() },
        )
    }

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
        val notificationCenter = get<InAppNotificationCenter>()
        val appContext = androidContext()
        ConnectivityManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            networkService = get(),
            networkSettings = get<SettingsManager>()
        ).also { manager ->
            // Observe border crossing events to show notifications
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                manager.borderCrossingEvents.collect { event ->
                    val vignette = VignetteService.infoFor(event.countryCode)
                    val title = appContext.getString(R.string.notification_border_crossing_title)
                    val inAppMessage = if (vignette != null) {
                        appContext.getString(
                            R.string.notification_border_crossing_message_vignette_action,
                            event.countryName,
                        )
                    } else {
                        appContext.getString(
                            R.string.notification_border_crossing_message,
                            event.countryName,
                        )
                    }
                    notificationCenter.add(
                        title = title,
                        message = inAppMessage,
                        actionUrl = vignette?.onlineShopUrl,
                    )
                    // AA HUN: vignette warning text only — no shop URLs/actions while driving
                    notificationHelper.showBorderCrossingNotification(
                        countryName = event.countryName,
                        requiresVignette = vignette != null,
                    )
                }
            }
        }
    }

    single { FuelForecastRepository(http = get(), db = get()) }

    single { AccidentProfileStore(androidContext()) }

    single {
        MaintenanceRepository(
            eventDao = get<AppDatabase>().maintenanceEventDao(),
            intervalDao = get<AppDatabase>().serviceIntervalDao(),
        )
    }

    single { MaintenanceReminderNotifier(androidContext()) }

    viewModel {
        AccidentViewModel(
            store = get(),
            settingsManager = get(),
        )
    }

    viewModel {
        MaintenanceViewModel(
            repository = get(),
            settingsManager = get(),
            reminderNotifier = get(),
        )
    }

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

/** Cap debug request/response payloads so oversized bodies cannot OOM the log store. */
private fun truncateDebugBody(body: String?, maxChars: Int = DEBUG_BODY_MAX_CHARS): String? {
    if (body == null) return null
    if (body.length <= maxChars) return body
    return body.take(maxChars) + "…[truncated ${body.length - maxChars} chars]"
}
