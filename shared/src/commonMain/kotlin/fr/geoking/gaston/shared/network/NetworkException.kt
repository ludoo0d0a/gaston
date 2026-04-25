package fr.geoking.gaston.shared.network

class NetworkException(val httpCode: Int?, message: String) : Exception(message)
