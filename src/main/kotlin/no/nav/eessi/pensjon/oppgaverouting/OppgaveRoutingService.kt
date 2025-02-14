package no.nav.eessi.pensjon.oppgaverouting

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OppgaveRoutingService(private val norg2Klient: Norg2Klient) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        logger.debug("personfdato: ${routingRequest.fdato},  bucType: ${routingRequest.bucType}, ytelseType: ${routingRequest.ytelseType}")

        if (routingRequest.aktorId == null) return Enhet.ID_OG_FORDELING

        val tildeltEnhet = tildelEnhet(routingRequest)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: ${routingRequest.bucType}, " +
                "Landkode: ${routingRequest.landkode}, " +
                "Fødselsdato: ${routingRequest.fdato}, " +
                "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                "Ytelsetype: ${routingRequest.ytelseType}")

        return tildeltEnhet
    }

    private fun tildelEnhet(routingRequest: OppgaveRoutingRequest): Enhet {

        if(routingRequest.bucType == BucType.P_BUC_01){
            val norgKlientRequest = NorgKlientRequest(routingRequest.diskresjonskode?.name, routingRequest.landkode, routingRequest.geografiskTilknytning)
            val norgEnhet = hentNorg2Enhet(norgKlientRequest, routingRequest.bucType)
            val egenDefinertEnhet = BucTilEnhetHandlerCreator.getHandler(routingRequest.bucType).hentEnhet(routingRequest)

            if(egenDefinertEnhet == Enhet.AUTOMATISK_JOURNALFORING){
                return Enhet.AUTOMATISK_JOURNALFORING
            }
            else if(norgEnhet != null) {
                return norgEnhet
            }
            return egenDefinertEnhet
        }
        return BucTilEnhetHandlerCreator.getHandler(routingRequest.bucType).hentEnhet(routingRequest)
    }

    @VisibleForTesting
    fun hentNorg2Enhet(person: NorgKlientRequest, bucType: BucType?): Enhet? {
        return try {
            val enhetVerdi = norg2Klient.hentArbeidsfordelingEnhet(person)
            logger.info("Norg2tildeltEnhet: $enhetVerdi")
            enhetVerdi?.let { Enhet.getEnhet(it) }
        } catch (ex: Exception) {
            logger.error("Ukjent feil oppstod; ${ex.message}")
            null
        }
    }
}
