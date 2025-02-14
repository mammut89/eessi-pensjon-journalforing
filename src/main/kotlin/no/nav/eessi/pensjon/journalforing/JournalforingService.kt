package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import javax.annotation.PostConstruct

@Service
class JournalforingService(private val euxKlient: EuxKlient,
                           private val journalpostService: JournalpostService,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val oppgaveHandler: OppgaveHandler,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private lateinit var journalforOgOpprettOppgaveForSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        journalforOgOpprettOppgaveForSed = metricsHelper.init("journalforOgOpprettOppgaveForSed")
    }

    fun journalfor(sedHendelseModel: SedHendelseModel,
                   hendelseType: HendelseType,
                   identifisertPerson: IdentifisertPerson?,
                   fdato: LocalDate,
                   ytelseType: YtelseType?,
                   offset: Long = 0,
                   sakInformasjon: SakInformasjon?) {
        journalforOgOpprettOppgaveForSed.measure {
            try {
                logger.info(
                    """
                    **********
                    rinadokumentID: ${sedHendelseModel.rinaDokumentId} rinasakID: ${sedHendelseModel.rinaSakId} sedType: ${sedHendelseModel.sedType?.name} bucType: ${sedHendelseModel.bucType}
                    kafka offset: $offset, hentSak PESYS saknr: ${sakInformasjon?.sakId} sakType: ${sakInformasjon?.sakType} på aktoerid: ${identifisertPerson?.aktoerId} ytelseType: $ytelseType
                    **********
                    """.trimIndent()
                )

                // Henter dokumenter
                val sedDokumenterJSON = euxKlient.hentSedDokumenter(sedHendelseModel.rinaSakId, sedHendelseModel.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelseModel.rinaSakId}, ${sedHendelseModel.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelseModel.sedType!!)

                val tildeltEnhet = oppgaveRoutingService.route(
                        OppgaveRoutingRequest.fra(identifisertPerson, fdato, ytelseType, sedHendelseModel, hendelseType, sakInformasjon)
                )

                val arkivsaksnummer = sakInformasjon?.sakId.takeIf { tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING }

                // Oppretter journalpost
                val journalPostResponse = journalpostService.opprettJournalpost(
                        rinaSakId = sedHendelseModel.rinaSakId,
                        fnr = identifisertPerson?.personRelasjon?.fnr,
                        personNavn = identifisertPerson?.personNavn,
                        bucType = sedHendelseModel.bucType!!,
                        sedType = sedHendelseModel.sedType,
                        sedHendelseType = hendelseType,
                        journalfoerendeEnhet = tildeltEnhet,
                        arkivsaksnummer = arkivsaksnummer,
                        dokumenter = documents,
                        avsenderLand = sedHendelseModel.avsenderLand,
                        avsenderNavn = sedHendelseModel.avsenderNavn,
                        ytelseType = ytelseType
                )

                // Oppdaterer distribusjonsinfo
                if (tildeltEnhet == Enhet.AUTOMATISK_JOURNALFORING) {
                    journalpostService.oppdaterDistribusjonsinfo(journalPostResponse!!.journalpostId)
                }

                val sedType = sedHendelseModel.sedType
                val aktoerId = identifisertPerson?.aktoerId

                if (!journalPostResponse!!.journalpostferdigstilt) {
                    val melding = OppgaveMelding(sedType, journalPostResponse.journalpostId, tildeltEnhet, aktoerId, sedHendelseModel.rinaSakId, hendelseType, null)
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }

                if (uSupporterteVedlegg.isNotEmpty()) {
                    val melding = OppgaveMelding(sedType, null, tildeltEnhet, aktoerId, sedHendelseModel.rinaSakId, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))
                    oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(melding)
                }

            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: MissingKotlinParameterException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<EuxDokument>): String {
        return uSupporterteVedlegg.joinToString(separator = "") { it.filnavn + " " }
    }
}
