package fr.geoking.gaston.api.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Decodes JSON numbers or numeric strings into [Double] (e.g. Croatia MZOE lat/long fields).
 */
object StringOrDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.doubleOrNull
                    ?: element.content.replace(',', '.').toDoubleOrNull()
                    ?: 0.0
            }
        }
        return decoder.decodeDouble()
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}
