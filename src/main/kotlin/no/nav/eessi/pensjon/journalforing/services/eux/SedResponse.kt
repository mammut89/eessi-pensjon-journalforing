package no.nav.eessi.pensjon.journalforing.services.eux


data class SedResponse(
        val pin: List<Pin>
)

data class Pin(val sektor: String,
                    val identifikator: String,
                    val land: String)
