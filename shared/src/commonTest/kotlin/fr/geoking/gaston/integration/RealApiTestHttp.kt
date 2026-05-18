package fr.geoking.gaston.integration

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

internal fun createRealApiHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        defaultRequest {
            headers.append(HttpHeaders.UserAgent, "Gaston-IntegrationTests/1.0")
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 90_000
        }
    }
