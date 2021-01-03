package no.nav.eessi.pensjon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import software.amazon.codeguruprofilerjavaagent.Profiler


@SpringBootApplication
@EnableRetry
class EessiPensjonJournalforingApplication

fun main(args: Array<String>) {
	
	Profiler.builder()
        	.profilingGroupName("MyProfilingGroup")
        	.build()
        	.start()
	
	runApplication<EessiPensjonJournalforingApplication>(*args)
}


