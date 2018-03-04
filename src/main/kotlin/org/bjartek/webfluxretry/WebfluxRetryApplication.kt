package org.bjartek.webfluxretry

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.toMono
import reactor.retry.retryExponentialBackoff
import java.time.Duration
import java.util.Random


@SpringBootApplication
class WebfluxRetryApplication

fun main(args: Array<String>) {
    runApplication<WebfluxRetryApplication>(*args)
}

@RestController
class RetryController(val webClientBuilder: WebClient.Builder) {


    val logger = LoggerFactory.getLogger(RetryController::class.java)
    val random = Random()

    @GetMapping("/sometimes")
    fun sometimes(): String {

        val number = random.nextDouble()
        if (number < 0.7) {
            throw RuntimeException("I failed $number")
        }

        logger.info("Succeeded with number=$number")

        return "yay $number"

    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<String> {
        logger.error("Exception with message=${ex.message}")
        return ResponseEntity.status(503).body(ex.message)
    }

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClientResponseException(ex: WebClientResponseException): ResponseEntity<String> {
        logger.error("Error from WebClient - Status {}, Body {}", ex.rawStatusCode, ex.responseBodyAsString)
        return ResponseEntity.status(ex.rawStatusCode).body(ex.responseBodyAsString)
    }

    @GetMapping("/call")
    fun callSometimes(): Flux<String> {

        val client = webClientBuilder
                .baseUrl("http://localhost:8080")
                .build()

        return client.get()
                .uri("/sometimes")
                .retrieve()
                .bodyToMono<String>()
                .retryExponentialBackoff(3, Duration.ofMillis(200), doOnRetry = {
                    logger.info("iteration=${it.iteration()}")
                })

    }


}



