package fr.geoking.gaston.persistence

import androidx.room.Entity

@Entity(tableName = "market_daily_quotes", primaryKeys = ["symbol", "day"])
data class MarketDailyQuoteEntity(
    val symbol: String,
    /** yyyy-MM-dd */
    val day: String,
    val close: Double,
    val fetchedAtMs: Long
)
