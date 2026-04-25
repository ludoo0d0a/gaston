package fr.geoking.gaston.shared.logging

import co.touchlab.kermit.Logger

val log: Logger by lazy { Logger.withTag("VoiceAI") }