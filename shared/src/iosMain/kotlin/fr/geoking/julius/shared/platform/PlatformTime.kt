package fr.geoking.gaston.shared.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun getCurrentTimeMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
