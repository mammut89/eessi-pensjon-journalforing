package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun hentAlleSeds(seds: Map<String, String?>): List<String?> {
        return seds.map { it.value }.toList()
    }

    fun hentAlleSedIBuc(rinaSakId: String): Map<String, String?> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            val sedType = pair.second
            sedType to euxKlient.hentSed(rinaSakId, sedDocumentId)
        }.toMap()
    }

    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: Map<String, String?>): YtelseType? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            val r005Sed = alleSedIBuc[SedType.R005.name]
            if (r005Sed != null) {
                val sedRootNode = mapper.readTree(r005Sed)
                return when (filterYtelseTypeR005(sedRootNode)) {
                    "alderspensjon" -> YtelseType.ALDER
                    "uførepensjon" -> YtelseType.UFOREP
                    "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> YtelseType.GJENLEV
                    else -> throw RuntimeException("Klarte ikke å finne ytelsetype for R_BUC_02")
                }
            }
            //hent ytelsetype fra P15000 overgang fra papir til rina. (saktype)
        } else if (sedHendelse.sedType == SedType.P15000) {
            val sed = alleSedIBuc[SedType.P15000.name]
            if (sed != null) {
                val sedRootNode = mapper.readTree(sed)
                return when (sedRootNode.get("nav").get("krav").get("type").textValue()) {
                    "02" -> YtelseType.GJENLEV
                    "03" -> YtelseType.UFOREP
                    else -> YtelseType.ALDER
                }
            }
        }
        return null
    }

    private fun filterYtelseTypeR005(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/tilbakekreving")
                .findValue("feilutbetaling")
                .findValue("type")
                .textValue()
    }

    fun hentPensjonSakFraSED(aktoerId: String, alleSedIBuc: Map<String, String?>, sedType: SedType?, bucType: BucType): SakInformasjon? {
        logger.debug("aktoerid: $aktoerId, sedType: ${sedType?.name} og bucType: ${bucType?.name}")
        val map = hentSakIdFraSED(alleSedIBuc)

        logger.debug(map.toJson())

        if (map.isNotEmpty()) {
            if (bucType == BucType.P_BUC_05) {
                val sedSakP8000 = map.getOrDefault("P8000", "")
                val currentSedsak = map.getOrDefault(sedType?.name, "")
                logger.info("P_BUC_05 Prøver å hente inn saknr fra SED P8000: $sedSakP8000 og ${sedType?.name}: $currentSedsak")
                if (sedSakP8000 == currentSedsak) {
                    val result = validerSakIdFraSEDogReturnerPensjonSak(aktoerId, listOf(sedSakP8000))
                    logger.debug("Følgende sakInformasjon funet: ${result?.toJson()}")
                    return result
                }
                logger.warn("saknr ikke likt som P8000 ingen saknr funnet fra P_BUC_05")
                return null
            } else {
                return validerSakIdFraSEDogReturnerPensjonSak(aktoerId, map.values.toList())
            }
        } else {
            return null
        }
    }

    private fun hentSakIdFraSED(alleSedIBuc: Map<String, String?>): Map<String, String> {
        //val list = mutableListOf<String>()
        val map = mutableMapOf<String, String>()

        alleSedIBuc.forEach { (_, sed) ->
            val sedRootNode = mapper.readTree(sed)
            val sedType = sedRootNode.get("sed").textValue()
            val eessi = filterEESSIsak(sedRootNode.get("nav"))
            logger.debug("eessi saknummer: $eessi")
            val sakid = eessi?.let { trimSakidString(it) }
            logger.debug("trimmet saknummer: $sakid")
            if (sakid != null && sakid.isNotBlank()) {
                map.put(sedType, sakid)
                logger.debug("legger sakid til liste...")
            }
        }
        return map
    }

    private fun filterEESSIsak(navNode: JsonNode): String? {
        val essiSakSubNode = navNode.findValue("eessisak") ?: return null
        return essiSakSubNode
                .filter { pin -> pin.get("land").textValue() == "NO" }
                .map { pin -> pin.get("saksnummer").textValue() }
                .lastOrNull()
    }

    fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")

    private fun validerSakIdFraSEDogReturnerPensjonSak(aktoerId: String, list: List<String>): SakInformasjon? {
        val saklist = fagmodulKlient.hentPensjonSaklist(aktoerId)
        logger.debug("aktoerid: $aktoerId sedSak: $list penSak: ${saklist.toJson()}")
        return if (saklist.size == 1) saklist.firstOrNull{ it.sakId in list }
        else saklist.firstOrNull {  it.sakId in list && it.sakType != YtelseType.GENRL }
    }

}
