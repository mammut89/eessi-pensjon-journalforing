package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SedFnrSøkTest {

    val sedFnrSøk = SedFnrSøk()

    @Test
    fun `Gitt en SED med flere norske fnr i Pin-identifikator feltet når det søkes etter fnr i SED så returner alle norske fnr`() {
        // Gitt
        val sedJson = javaClass.getResource("/sed/P2000-NAV.json").readText()
        val sed = mapJsonToAny(sedJson, typeRefs<SED>())

        // Når
        val funnedeFnr = sedFnrSøk.finnAlleFnrDnrISed(sed)

        // Så
        assertTrue(funnedeFnr.containsAll(listOf("09097097341", "97097097001", "97097097002", "97097097003")))
        assertEquals(funnedeFnr.size, 4)
    }

    @Test
    fun `Gitt en SED med flere norske fnr i Pin-kompetenteuland feltet når det søkes etter fnr i SED så returner alle norske fnr`() {
        // Gitt
        val sedJson = javaClass.getResource("/sed/H021-NAV.json").readText()
        val sed = mapJsonToAny(sedJson, typeRefs<SED>())

        // Når
        val funnedeFnr = sedFnrSøk.finnAlleFnrDnrISed(sed)

        // SÅ
        assertTrue(funnedeFnr.containsAll(listOf("12345678910", "12345678990")))
        assertEquals(funnedeFnr.size, 2)
    }
}