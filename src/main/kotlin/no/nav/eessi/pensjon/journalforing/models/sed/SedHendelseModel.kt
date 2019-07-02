package no.nav.eessi.pensjon.journalforing.models.sed

import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.models.SedType

data class SedHendelseModel (
        val id: Long? = 0,
        val sedId: String? = null,
        val sektorKode: String,
        val bucType: BucType?,
        val rinaSakId: String,
        val avsenderId: String,
        val avsenderNavn: String,
        val avsenderLand: String? = null,
        val mottakerId: String,
        val mottakerNavn: String,
        val mottakerLand: String? = null,
        val rinaDokumentId: String,
        val rinaDokumentVersjon: String? = null,
        val sedType: SedType?,
        val navBruker: String? = null
) {


    enum class SedHendelseType {
        SENDT,
        MOTTATT
    }
}

