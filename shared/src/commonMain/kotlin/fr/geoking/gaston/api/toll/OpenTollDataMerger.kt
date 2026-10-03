package fr.geoking.gaston.api.toll

/**
 * Merges multiple OpenTollData models (one file per concession) into a single model.
 * Later files overwrite booth / open-price keys on conflict; networks are concatenated.
 */
object OpenTollDataMerger {

    fun merge(parts: List<OpenTollDataModel>): OpenTollDataModel {
        if (parts.isEmpty()) return OpenTollDataModel()
        if (parts.size == 1) return parts[0]
        val networks = mutableListOf<OpenTollNetwork>()
        val tollDescription = linkedMapOf<String, TollBoothDescription>()
        val openTollPrice = linkedMapOf<String, OpenTollPriceEntry>()
        for (part in parts) {
            networks.addAll(part.networks)
            tollDescription.putAll(part.tollDescription)
            openTollPrice.putAll(part.openTollPrice)
        }
        return OpenTollDataModel(
            networks = networks,
            tollDescription = tollDescription,
            openTollPrice = openTollPrice
        )
    }
}
