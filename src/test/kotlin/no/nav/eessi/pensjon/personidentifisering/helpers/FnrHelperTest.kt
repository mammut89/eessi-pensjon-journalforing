package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nhaarman.mockitokotlin2.doReturn
import no.nav.eessi.pensjon.json.validateJson
import no.nav.eessi.pensjon.personidentifisering.klienter.BrukerMock
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FnrHelperTest {

    @Mock
    private lateinit var personV3Klient: PersonV3Klient

    private lateinit var helper: FnrHelper

    private lateinit var mockExpectedPerson: Bruker

    @BeforeEach
    fun setup() {
        helper = FnrHelper(personV3Klient)

        mockExpectedPerson = BrukerMock.createWith(landkoder = true)!!

        //PERSONV3 - HENT PERSON
        doReturn(mockExpectedPerson)
                .`when`(personV3Klient)
                .hentPerson(ArgumentMatchers.anyString())

    }

    @Test
    fun `filtrer norsk pin annenperson med rolle 01`()   {
        val mapper = jacksonObjectMapper()
        val p2000json = getTestJsonFile("P2000-NAV.json")
        assertEquals(null, helper.filterAnnenpersonPinNode(mapper.readTree(p2000json)))

        val p10000json = getTestJsonFile("P10000-01Gjenlevende-NAV.json")
        val expected = "287654321"
        val actual  = helper.filterAnnenpersonPinNode(mapper.readTree(p10000json))
        assertEquals(expected, actual)

    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P2100 også P2000 etter norsk personnr`() {
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json")))

        val expectedFnr = "97097097000"
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc etter norsk personnr`() {
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P2000-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))

        val expectedFnr = "97097097000"
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom beste Sed på valgt buc P15000 alder eller ufor etter norsk personnr`() {
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000-NAV.json")))

        val expectedFnr = "21712000000"
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom beste Sed paa valgt buc P15000 gjenlevende etter norsk personnr`() {
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getTestJsonFile("P2100-PinDK-NAV.json"),
                getTestJsonFile("P15000Gjennlevende-NAV.json")
        ))
        val expectedFnr = "97097097000"
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere personer etter fnr på avød`() {
        val expectedFnr = "28115518943"
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getSedTestJsonFile("R005-avdod-enke-NAV.json")))
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person returnerer fnr`() {
        val expectedFnr = "28115518943"
        val actual = helper.getPersonOgFnrFraSeder(listOf(
                getSedTestJsonFile("R_BUC_02-R005-AP.json")))
        assertEquals(Pair(mockExpectedPerson, expectedFnr), actual)
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med kun en person uten pin`() {
        assertThrows<RuntimeException> {
            helper.getPersonOgFnrFraSeder(listOf(getSedTestJsonFile("R_BUC_02-R005-IkkePin.json")))
        }
    }

    @Test
    fun `leter igjennom R_BUC_02 og R005 med flere person ikke avdød`() {
        assertThrows<RuntimeException> {
            helper.getPersonOgFnrFraSeder(listOf(getSedTestJsonFile("R005-enke-ikkeavdod-NAV.json")))
        }
    }

    private fun getSedTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/sed/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        Assertions.assertTrue(validateJson(json))
        return json
    }

    private fun getTestJsonFile(filename: String): String {
        val filepath = "src/test/resources/buc/${filename}"
        val json = String(Files.readAllBytes(Paths.get(filepath)))
        Assertions.assertTrue(validateJson(json))
        return json
    }
}