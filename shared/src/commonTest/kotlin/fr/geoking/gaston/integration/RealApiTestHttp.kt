package fr.geoking.gaston.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun createRealApiHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        install(HttpRedirect) {
            checkHttpMethod = false
        }
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "Gaston-IntegrationTests/1.0")
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 90_000
        }
    }
