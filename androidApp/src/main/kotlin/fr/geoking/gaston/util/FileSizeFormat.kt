package fr.geoking.gaston.util

/** Formats a byte count as a human-readable size, e.g. "106 MB" or "1.2 GB". */
fun formatStorageSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
