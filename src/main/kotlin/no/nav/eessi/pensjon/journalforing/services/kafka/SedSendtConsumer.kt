package no.nav.eessi.pensjon.journalforing.services.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class SedConsumer(val pdfService: PdfService,
                  val journalpostService: JournalpostService,
                  val oppgaveService: OppgaveService,
                  val aktoerregisterService: AktoerregisterService) {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)


        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            val pdfBody = pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            var aktoerId: String? = null
            if(sedHendelse.navBruker != null) {
                aktoerId = aktoerregisterService.hentGjeldendeAktorIdForNorskIdent(sedHendelse.navBruker)
            }
            val journalPostResponse = journalpostService.opprettJournalpost(sedHendelse = sedHendelse, pdfBody= pdfBody!!, forsokFerdigstill = false)
            oppgaveService.opprettOppgave(sedHendelse, journalPostResponse, aktoerId)
            // acknowledgment.acknowledge()
        }
    }
}