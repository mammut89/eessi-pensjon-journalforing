package no.nav.eessi.pensjon.models.sed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.SedType

@JsonIgnoreProperties(ignoreUnknown = true)
data class SED(
        @JsonProperty("sed")
        val type: SedType,

        val sedGVer: String? = null,
        val sedVer: String? = null,
        val nav: Nav? = null,
        val pensjon: Pensjon? = null,
        val tilbakekreving: Tilbakekreving? = null
) {
    companion object {
        fun fromJson(sed: String): SED = mapJsonToAny(sed, typeRefs(), true)
    }

    override fun toString(): String = this.toJson()
}
