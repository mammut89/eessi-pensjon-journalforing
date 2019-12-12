package no.nav.eessi.pensjon.services.pesys

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class PenService(private val bestemSakOidcRestTemplate: RestTemplate,
                 @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    val logger: Logger by lazy { LoggerFactory.getLogger(PenService::class.java) }
    val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

    /**
     * Henter pesys sakID for en gitt aktørID og ytelsetype
     *
     * Foreløbig er alder, uføre og gjenlevende støttet i her men tjenesten støtter alle typer
     */
    fun hentSakId(aktoerId: String, bucType: BucType) : String? {

        val ytelseType = when(bucType) {
            BucType.P_BUC_01 -> YtelseType.ALDER
            BucType.P_BUC_02 -> YtelseType.GJENLEV
            BucType.P_BUC_03 -> YtelseType.UFOREP
            else -> return null
        }

        val resp = kallBestemSak(aktoerId, ytelseType)
        if(resp != null && resp.sakInformasjon.size == 1) {
            return resp.sakInformasjon.first().sakId
        }
        return null
    }

     fun kallBestemSak(aktoerId: String, ytelseType: YtelseType): BestemSakResponse? {
        val randomId = UUID.randomUUID().toString()
        val requestBody = BestemSakRequest(aktoerId, ytelseType, randomId, randomId)

        return metricsHelper.measure("hentPesysSaker") {
            return@measure try {
                logger.info("Kaller bestemSak i PESYS")
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val response = bestemSakOidcRestTemplate.exchange(
                        "/",
                        HttpMethod.POST,
                        HttpEntity(requestBody.toJson(), headers),
                        String::class.java)
                mapper.readValue(response.body, BestemSakResponse::class.java)
            } catch (ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under kall til bestemSkak i PESYS ex: $ex body: ${ex.responseBodyAsString}")
                //TODO Throw så fort feature er testet ferdig
                // throw RuntimeException("En feil oppstod under kall til bestemSkak i PESYS ex: ${ex.message} body: ${ex.responseBodyAsString}")
                null
            } catch (ex: Exception) {
                logger.error("En feil oppstod under kall til bestemSkak i PESYS ex: $ex")
                //TODO Throw så fort feature er testet ferdig
                // throw RuntimeException("En feil oppstod under kall til bestemSkak i PESYS ex: ${ex.message}")
                null
            }
        }
    }
}

class BestemSakRequest ( val aktoerId: String,
                         val ytelseType: YtelseType,
                         val callId: String,
                         val consumerId: String)

class BestemSakResponse ( val feil: BestemSakFeil,
                          val sakInformasjon: List<SakInformasjon>)

class BestemSakFeil( val feilKode: String,  val feilmelding: String)

class SakInformasjon( val sakId: String,
                           val sakType: YtelseType,
                           val sakStatus: SakStatus,
                           val saksbehandlendeEnhetId: String,
                           val nyopprettet: Boolean)

enum class SakStatus {
    OPPRETTET,
    TIL_BEHANDLING,
    AVSLUTTET,
    LOPENDE
}

enum class YtelseType {
    OMSORG,
    ALDER,
    GJENLEV,
    BARNEP,
    UFOREP,
    GENRL
}

