package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.SedDokumentHelper
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import no.nav.eessi.pensjon.models.HendelseType.*
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.PartitionOffset
import org.springframework.kafka.annotation.TopicPartition
import java.util.*

@Service
class SedListener(
        private val journalforingService: JournalforingService,
        private val personidentifiseringService: PersonidentifiseringService,
        private val sedDokumentHelper: SedDokumentHelper,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)
    private val latch = CountDownLatch(6)

    fun getLatch(): CountDownLatch {
        return latch
    }


    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consumeSedSendt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeOutgoingSed") {
                logger.info("Innkommet sedSendt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug(hendelse)
                try {
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)

                    if (sedHendelse.sektorKode == "P") {
                        logger.info("*** Starter utgående journalføring for SED innen Pensjonsektor type: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                        val alleSedIBuc  = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                        val identifisertPerson = personidentifiseringService.identifiserPerson(sedHendelse, alleSedIBuc)
                        journalforingService.journalfor(sedHendelse, SENDT, identifisertPerson)
                    }
                    acknowledgment.acknowledge()
                    logger.info("Acket sedSendt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                } catch (ex: Exception) {
                    logger.error(
                            "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                                    "${ex.message}",
                            ex
                    )
                    throw RuntimeException(ex.message)
                }
            latch.countDown()
            }
        }
    }

//    @KafkaListener(topics = ["\${kafka.sedMottatt.topic}"], groupId = "\${kafka.sedMottatt.groupid}")
    @KafkaListener(groupId = "\${kafka.sedMottatt.groupid}",
            topicPartitions = [TopicPartition(topic = "\${kafka.sedMottatt.topic}",
                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "16000")])])
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeIncomingSed") {

                //rerun journal liste med offset id som må kjøres på nytt
                //offsett å hente opp (P2000, P4000 og en P5000 henter tapte journalføringer på oppgave)
                if (cr.offset() == 16293L || cr.offset() == 16287L) {

                    logger.info("Innkommet sedMottatt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                    logger.debug(hendelse)
/*
                    følgende feilet mot topic oppgave med dobbelt envclass (-p-p)
                    Acket sedMottatt melding med offset: 16287 i partisjon 0
                    Acket sedMottatt melding med offset: 16293 i partisjon 0
*/
                    try {
                        val sedHendelse = SedHendelseModel.fromJson(hendelse)

                        if (sedHendelse.sektorKode == "P") {
                            logger.info("*** Starter innkommende journalføring for SED innen Pensjonsektor type: ${sedHendelse.bucType} bucid: ${sedHendelse.rinaSakId} ***")
                            val alleSedIBuc = sedDokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId)
                            val identifisertPerson = personidentifiseringService.identifiserPerson(sedHendelse, alleSedIBuc)
                            journalforingService.journalfor(sedHendelse, MOTTATT, identifisertPerson)
                        }
                        acknowledgment.acknowledge()
                        logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                    } catch (ex: Exception) {
                        logger.error(
                                "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                                        "${ex.message}",
                                ex
                        )
                        throw RuntimeException(ex.message)
                    }

                } else {

                    acknowledgment.acknowledge()

                }

            }
        }
    }

}
