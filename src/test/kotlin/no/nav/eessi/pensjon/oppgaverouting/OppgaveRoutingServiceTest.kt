package no.nav.eessi.pensjon.oppgaverouting

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel.Enhet.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class OppgaveRoutingServiceTest {

    @Spy
    private lateinit var norg2Klient: Norg2Klient

    private lateinit var routingService: OppgaveRoutingService

    @BeforeEach
    fun setup() {
        routingService = OppgaveRoutingService(norg2Klient)
    }

    companion object {
        const val dummyTilknytning = "032342"
        val MANGLER_LAND = null as String?
        const val NORGE: String = "NOR"
        const val UTLAND: String = "SE"

        // NFP krets er en person mellom 18 og 60 år
        val alder18aar: LocalDate = LocalDate.now().minusYears(18).minusDays(1)
        val alder59aar: LocalDate = LocalDate.now().minusYears(60).plusDays(1)


        // NAY krets er en person yngre enn 18 år eller eldre enn 60 år
        val alder17aar: LocalDate = LocalDate.now().minusYears(18).plusDays(1)
        val alder60aar: LocalDate = LocalDate.now().minusYears(60)

    }

    private fun irrelevantDato() = LocalDate.MIN

    @Test
    fun `Gitt manglende fnr naar oppgave routes saa send oppgave til ID_OG_FORDELING`() {
        val enhet = routingService.route(OppgaveRoutingRequest(fdato = irrelevantDato(), landkode = MANGLER_LAND, geografiskTilknytning = dummyTilknytning, bosatt = null))
        assertEquals(enhet, ID_OG_FORDELING)
    }

    @Test
    fun `Gitt manglende buc-type saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(OppgaveRoutingRequest(fnr = "010101010101", fdato = irrelevantDato(), landkode = MANGLER_LAND, bosatt = null))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(OppgaveRoutingRequest(fnr = "010101010101", fdato = irrelevantDato(), landkode = MANGLER_LAND, bosatt = null))
        assertEquals(enhet, PENSJON_UTLAND)
    }

    @Test
    fun `Routing for mottatt H_BUC_07`() {
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, bosatt = null)))

        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(NFP_UTLAND_OSLO, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, landkode = NORGE, bosatt = null)))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fdato = alder60aar, landkode = NORGE, bosatt = null)))

    }

    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 ingen ytelse`() {
        assertEquals(ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = NORGE,
                        geografiskTilknytning = dummyTilknytning,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 alderpensjon ytelse`() {
        assertEquals(PENSJON_UTLAND, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        ytelseType = YtelseType.ALDER,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))
    }

    @Test
    fun `Routing av mottatte sed R005 på R_BUC_02 uforepensjon ytelse`() {
        assertEquals(UFORE_UTLAND, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        ytelseType = YtelseType.UFOREP,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 skal gi en routing til UFORE_UTLAND`() {
        assertEquals(OKONOMI_PENSJON, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        ytelseType = YtelseType.UFOREP,
                        sedType = SedType.R004,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))
    }

    @Test
    fun `Routing av mottatte sed R004 på R_BUC_02 ukjent ident`() {
        assertEquals(ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                        fnr = null,
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        ytelseType = YtelseType.UFOREP,
                        sedType = SedType.R004,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        bosatt = null)
        ))
    }

    @Test
    fun `Routing av mottatte sed R_BUC_02 med mer enn én person routes til ID_OG_FORDELING`() {
        val forsikret = IdentifisertPerson(
                "123",
                "Testern",
                null,
                null,
                "010",
                PersonRelasjon("12345678910", Relasjon.FORSIKRET))
        val avod = IdentifisertPerson(
                "234",
                "Avdod",
                null,
                null,
                "010",
                PersonRelasjon("22345678910", Relasjon.AVDOD))
        forsikret.personListe = listOf(forsikret, avod)

        val enhetresult = routingService.route(
                OppgaveRoutingRequest(
                        fnr = "123123123",
                        fdato = irrelevantDato(),
                        landkode = null,
                        geografiskTilknytning = dummyTilknytning,
                        ytelseType = YtelseType.ALDER,
                        sedType = SedType.R005,
                        hendelseType = HendelseType.MOTTATT,
                        sakStatus = null,
                        identifisertPerson = forsikret,
                        bosatt = null)
        )

        assertEquals(ID_OG_FORDELING, enhetresult)

    }


    // ved bruk av fil kan jeg bruke denne: R_BUC_02-R005-AP.json
    @Test
    fun `Routing av utgående seder i R_BUC_02`() {
        assertEquals(ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = NORGE,
                        geografiskTilknytning = dummyTilknytning,
                        sedType = SedType.R005,
                        hendelseType = HendelseType.SENDT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))

        assertEquals(OKONOMI_PENSJON, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = NORGE,
                        geografiskTilknytning = dummyTilknytning,
                        sedType = SedType.R004,
                        hendelseType = HendelseType.SENDT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))

        assertEquals(OKONOMI_PENSJON, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        sedType = SedType.R004,
                        hendelseType = HendelseType.SENDT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))

        assertEquals(ID_OG_FORDELING, routingService.route(
                OppgaveRoutingRequest(
                        fnr = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = UTLAND,
                        geografiskTilknytning = dummyTilknytning,
                        sedType = SedType.R005,
                        hendelseType = HendelseType.SENDT,
                        sakStatus = null,
                        identifisertPerson = mockerEnPerson(),
                        bosatt = null
                )
        ))

    }

    @Test
    fun `Routing for vanlige BUC'er`() {


        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), geografiskTilknytning = dummyTilknytning, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, geografiskTilknytning = dummyTilknytning, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, geografiskTilknytning = dummyTilknytning, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bosatt = null)))


        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE, bosatt = null)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET, bosatt = null)))

        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.BARNEP, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, ytelseType = YtelseType.ALDER, bosatt = null)))

        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bosatt = null)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, ytelseType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE, bosatt = null)))
        assertEquals(ID_OG_FORDELING, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, ytelseType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, ytelseType = YtelseType.BARNEP, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, ytelseType = YtelseType.ALDER, bosatt = null)))



        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = irrelevantDato(), landkode = UTLAND, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND,  ytelseType = YtelseType.ALDER, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND,  ytelseType = YtelseType.ALDER, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND,  ytelseType = YtelseType.GJENLEV, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND,  ytelseType = YtelseType.GJENLEV, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar,  ytelseType = YtelseType.UFOREP, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = NORGE,  ytelseType = YtelseType.UFOREP, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder18aar, landkode = UTLAND,  ytelseType = YtelseType.UFOREP, bosatt = null)))

        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar,  ytelseType = YtelseType.UFOREP, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = NORGE,  ytelseType = YtelseType.UFOREP, bosatt = null)))
        assertEquals(UFORE_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder17aar, landkode = UTLAND,  ytelseType = YtelseType.UFOREP, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder59aar,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder59aar, landkode = NORGE,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder59aar, landkode = UTLAND,  ytelseType = YtelseType.ALDER, bosatt = null)))

        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(NFP_UTLAND_AALESUND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, landkode = NORGE,  ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, landkode = UTLAND,  ytelseType = YtelseType.ALDER, bosatt = null)))

        assertEquals(DISKRESJONSKODE, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, diskresjonskode = "SPSF", geografiskTilknytning = dummyTilknytning, ytelseType = YtelseType.ALDER, bosatt = null)))
        assertEquals(UFORE_UTLANDSTILSNITT, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, diskresjonskode = "SPFO", landkode = NORGE, ytelseType = YtelseType.UFOREP, bosatt = null)))
        assertEquals(PENSJON_UTLAND, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, diskresjonskode = "SPFO", landkode = UTLAND,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
        assertEquals(DISKRESJONSKODE, routingService.route(OppgaveRoutingRequest(fnr = "01010101010", fdato = alder60aar, diskresjonskode = "SPSF", landkode = UTLAND,  ytelseType = YtelseType.GJENLEV, bosatt = null)))
    }


    @Test
    fun `hentNorg2Enhet for bosatt utland`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig0001result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(landkode = "SVE"), BucType.P_BUC_01)
        val expected = PENSJON_UTLAND

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4803result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                BucType.P_BUC_01)
        val expected = NFP_UTLAND_OSLO

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt nord-Norge`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordelig4862result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "1102", landkode = "NOR"),
                BucType.P_BUC_01)
        val expected = NFP_UTLAND_AALESUND

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for diskresjonkode`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(
                geografiskTilknytning = "1102",
                landkode = "NOR",
                diskresjonskode = "SPSF"),
                BucType.P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge feil buc`() {
        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                BucType.P_BUC_03)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2`() {
        doReturn(listOf<Norg2ArbeidsfordelingItem>())
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                BucType.P_BUC_01)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge mock feil mot Norg2 error`() {
        doThrow(RuntimeException("dummy"))
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(geografiskTilknytning = "0322", landkode = "NOR"),
                BucType.P_BUC_01)
        val expected = null

        assertEquals(expected, actual)
    }

    @Test
    fun `hentNorg2Enhet for bosatt Norge med diskresjon`() {
        val enhetlist = mapJsonToAny(getJsonFileFromResource("norg2arbeidsfordeling2103result.json"), typeRefs<List<Norg2ArbeidsfordelingItem>>())
        doReturn(enhetlist)
                .whenever(norg2Klient).hentArbeidsfordelingEnheter(any())

        val actual = routingService.hentNorg2Enhet(NorgKlientRequest(
                geografiskTilknytning = "0322",
                landkode = "NOR",
                diskresjonskode = "SPSF"),
                BucType.P_BUC_01)
        val expected = DISKRESJONSKODE

        assertEquals(expected, actual)
    }

    @Test
    fun testEnumEnhets() {

        assertEquals(PENSJON_UTLAND, OppgaveRoutingModel.Enhet.getEnhet("0001"))

        assertEquals(NFP_UTLAND_OSLO, OppgaveRoutingModel.Enhet.getEnhet("4803"))

        assertEquals(DISKRESJONSKODE, OppgaveRoutingModel.Enhet.getEnhet("2103"))

    }

    private fun getJsonFileFromResource(filename: String): String {
        return String(Files.readAllBytes(Paths.get("src/test/resources/norg2/$filename")))
    }

    private fun mockerEnPerson() = IdentifisertPerson(
            "123",
            "Testern",
            null,
            "NO",
            "010",
            PersonRelasjon("12345678910", Relasjon.FORSIKRET))

}
