package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)
    private val mapper = jacksonObjectMapper()

    /**
     * leter etter et gyldig fnr i alle seder henter opp person i PersonV3
     * ved R_BUC_02 leter etter alle personer i Seder og lever liste
     */
    fun getPotensielleFnrFraSeder(seder: List<String?>): List<PersonRelasjon> {
        val fnrListe = mutableSetOf<PersonRelasjon>()
        var sedType: SedType

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)
                sedType = SedType.valueOf(sedRootNode.get("sed").textValue())

                if (sedType.kanInneholdeFnrEllerFdato) {
                    logger.info("SED: $sedType")
                    when (sedType) {
                        SedType.P2100 -> {
                            leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe, sedType = sedType)
                        }
                        SedType.P15000 -> {
                            val krav = try {
                                sedRootNode.get("nav").get("krav").get("type").textValue()
                            } catch (ex: Exception) {
                                logger.warn("krav ikke satt")
                                null
                            }
                            val ytelseType =  ytelseTypefraKravSed(krav)
                            logger.info("${sedType.name}, krav: $krav,  ytelsetype: $ytelseType")
                            if (krav == "02") {
                                logger.debug("legger til gjenlevende: ($ytelseType)")
                                leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe, ytelseType, sedType)
                            } else {
                                logger.debug("legger til forsikret: ($ytelseType)")
                                leggTilForsikretFnrHvisFinnes(sedRootNode, fnrListe, ytelseType, sedType)
                            }
                        }
                        SedType.R005 -> {
                            fnrListe.addAll(filterPinPersonR005(sedRootNode))
                        }
                        SedType.P5000, SedType.P6000 -> {
                            // Prøver å hente ut gjenlevende på andre seder enn P2100
                            leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe, sedType = sedType)
                        }
                        SedType.P8000 -> {
                            leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sedRootNode, fnrListe)
                        }
                        else -> {
                            // P10000, P9000
                            leggTilAnnenGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe, sedType = sedType)
                            //P2000 - P2200 -- andre..  (H070)
                            leggTilForsikretFnrHvisFinnes(sedRootNode, fnrListe, sedType = sedType)

                        }
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }

        return fnrListe.distinctBy { it.fnr }
    }

    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>, sedType: SedType) {
        filterAnnenpersonPinNode(sedRootNode)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, sedType = sedType))
        }
    }

    private fun leggTilForsikretFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>, ytelseType: YtelseType?= null, sedType: SedType) {
        filterPersonPinNode(sedRootNode)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, ytelseType, sedType = sedType))
        }
    }

    private fun ytelseTypefraKravSed(krav: String?): YtelseType? {
        return when (krav) {
            "01" -> YtelseType.ALDER
            "02" -> YtelseType.GJENLEV
            "03" -> YtelseType.UFOREP
            else -> null
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>, ytelseType: YtelseType? = null, sedType: SedType) {
        filterPersonPinNode(sedRootNode)?.let{
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET, ytelseType, sedType = sedType))
            logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")

        }
        filterGjenlevendePinNode(sedRootNode)?.let {
            val gjenlevendeRelasjon = finnGjenlevendeRelasjontilavdod(sedRootNode)
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, sedType = sedType))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }
            val gyldigeBarn = listOf("06","07","08","09")
            if ( gyldigeBarn.contains(gjenlevendeRelasjon) ) {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, YtelseType.BARNEP, sedType = sedType))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
            } else {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, YtelseType.GJENLEV, sedType = sedType))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
            }
        }
    }

    /**
     * P8000 - [01] Søker til etterlattepensjon
     * P8000 - [02] Forsørget/familiemedlem
     * P8000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>) {
        logger.debug("Leter i P8000")

        val sedType = SedType.P8000
        val personPin = filterPersonPinNode(sedRootNode)
        val annenPersonPin = filterAnnenpersonPinNodeUtenRolle(sedRootNode)
        val rolle = filterAnnenpersonRolle(sedRootNode)
        logger.debug("Personpin: $personPin AnnenPersonpin $annenPersonPin  Annenperson rolle : $rolle")

        //hvis to personer ingen rolle return uten pin..
        if (personPin != null && annenPersonPin != null && rolle == null) return
        //else if (annenPersonPin == null && rolle != null) return

        personPin?.run {
            fnrListe.add(PersonRelasjon(this, Relasjon.FORSIKRET, sedType = sedType))
            logger.debug("Legger til person ${Relasjon.FORSIKRET} relasjon")
        }

 //       annenPersonPin?.run {
            val annenPersonRelasjon = when (rolle) {
                "01" -> PersonRelasjon(annenPersonPin ?: "", Relasjon.GJENLEVENDE, sedType = sedType)
                "02" -> PersonRelasjon(annenPersonPin ?: "", Relasjon.FORSORGER, sedType = sedType)
                "03" -> PersonRelasjon(annenPersonPin ?: "", Relasjon.BARN, sedType = sedType)
                else -> PersonRelasjon(annenPersonPin ?: "", Relasjon.ANNET, sedType = sedType)
            }
            fnrListe.add(annenPersonRelasjon)
            logger.debug("Legger til person med relasjon: ${annenPersonRelasjon.relasjon}")
   //     }

    }

    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    fun filterAnnenpersonPinNode(node: JsonNode): String? {
        val subNode = node.at("/nav/annenperson") ?: return null
        val rolleNode = subNode.at("/person/rolle") ?: return null
        if (rolleNode.textValue() == "01") {
        return subNode.get("person")
                .findValue("pin")
                .filter { it.get("land").textValue() == "NO" }
                .map { it.get("identifikator").textValue() }
                .lastOrNull()
        }
        return null
    }

    fun filterAnnenpersonRolle(node: JsonNode): String? {
        val subNode = node.at("/nav/annenperson") ?: return null
        val rolleNode = subNode.at("/person/rolle") ?: return null
        return rolleNode.textValue()
    }

    fun filterAnnenpersonPinNodeUtenRolle(node: JsonNode): String? {
        val subNode = node.at("/nav/annenperson") ?: return null
        return finnPin(subNode.at("/person"))
    }

    private fun finnGjenlevendeRelasjontilavdod(sedRootNode: JsonNode): String? {
        return sedRootNode.at("/pensjon/gjenlevende/person/relasjontilavdod/relasjon").textValue()
    }

    private fun filterGjenlevendePinNode(sedRootNode: JsonNode): String? {
        return finnPin(sedRootNode.at("/pensjon/gjenlevende/person"))
    }

    private fun filterPersonPinNode(sedRootNode: JsonNode): String? {
        return finnPin(sedRootNode.at("/nav/bruker"))
    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * * hvis ingen intreffer returnerer vi null
     */
    private fun filterPinPersonR005(sedRootNode: JsonNode): MutableList<PersonRelasjon> {
        val subnode = sedRootNode.at("/nav/bruker").toList()

        val personRelasjoner = mutableListOf<PersonRelasjon>()
        subnode.forEach {
            val enkelNode = it.get("person")
            val pin = finnPin(enkelNode)
            val type = getType(it)
            personRelasjoner.add(PersonRelasjon(pin!!, type, sedType = SedType.R005))
        }
        return personRelasjoner
    }

    //Kun for R_BUC_02
    private fun getType(node: JsonNode): Relasjon {
        return when (node.get("tilbakekreving").get("status").get("type").textValue()) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }

    private fun finnPin(pinNode: JsonNode): String? {
        val subPinNode = pinNode.findValue("pin") ?: return null
         return subPinNode
                 .filter { pin -> pin.get("land").textValue() == "NO" }
                 .map { pin -> PersonidentifiseringService.trimFnrString(pin.get("identifikator").textValue()) }
                 .lastOrNull { pin -> PersonidentifiseringService.erFnrDnrFormat(pin) }
    }

}
