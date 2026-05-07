package fr.geoking.gaston.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StationPriceSampleEntity::class,
        MarketDailyQuoteEntity::class,
        LocalFuelAvgDailyEntity::class,
        FuelPricePredictionEntity::class,
        FuelPricePredictionScoreEntity::class,
        PoiCacheEntity::class,
        NationalFuelPriceEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationPriceSampleDao(): StationPriceSampleDao
    abstract fun marketDailyQuoteDao(): MarketDailyQuoteDao
    abstract fun localFuelAvgDailyDao(): LocalFuelAvgDailyDao
    abstract fun fuelPricePredictionDao(): FuelPricePredictionDao
    abstract fun fuelPricePredictionScoreDao(): FuelPricePredictionScoreDao
    abstract fun poiCacheDao(): PoiCacheDao
    abstract fun nationalFuelPriceDao(): NationalFuelPriceDao
}
