package fr.geoking.gaston.aac

/**
 * Minimal road-safety message channel for AAC Level A.
 */
object RoadSafetyMessages {
    private val frTips = listOf(
        "Respectez les distances de sécurité.",
        "Adaptez votre vitesse aux conditions de circulation.",
        "Restez vigilant dans les zones de danger.",
        "Bouclez votre ceinture et celle de vos passagers.",
    )

    fun tipForIndex(index: Int): String = frTips[index.mod(frTips.size)]

    fun randomTip(seed: Long = System.currentTimeMillis()): String =
        tipForIndex((seed % frTips.size).toInt())
}
