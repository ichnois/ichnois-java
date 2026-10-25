package io.ichnois.spring.boot.integrationtest

import io.ichnois.IchnoisCore
import io.ichnois.spring.tracing.IchnoisCoreSpan
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

// Classes and methods are open because Spring proxies them and this module doesn't apply the
// kotlin-spring plugin.

@SpringBootApplication
@EnableCaching
open class IntegrationTestApplication {
  // Built from Spring Boot's builders, which IchnoisCore customizes to trace outgoing requests.
  @Bean open fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()

  @Bean open fun webClient(builder: WebClient.Builder): WebClient = builder.build()

  // Traced through ichnois.clients.ok-http-enabled.
  @Bean open fun okHttpClient(): OkHttpClient = OkHttpClient()
}

/**
 * The URL of [path] on this app, which listens on a random port. Only works on a request thread,
 * because it reads the address from the current request.
 */
fun selfUrl(path: String): String =
  ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString()

open class Person(var firstName: String? = null, var lastName: String? = null)

@RestController
@RequestMapping("/person/")
open class PersonController(private val personService: PersonService) {
  private val logger = LoggerFactory.getLogger(PersonController::class.java)

  /** Reports to IchnoisCore through every API an app uses, then fails the request. */
  @GetMapping("{id}")
  open fun person(@PathVariable("id") id: Long): Person {
    IchnoisCore.addFeatureFlag("transaction-feature-flag", true)
    val ichnoisSpan = IchnoisCore.getSpan()!!.startChild("spanCreatedThroughIchnoisCoreApi")
    try {
      IchnoisCore.setAttribute("user.type", "admin")
      IchnoisCore.setAttribute("feature.version", 2)
      IchnoisCore.setAttribute("debug.enabled", true)
      IchnoisCore.logger().warn("warn IchnoisCore logging")
      IchnoisCore.logger().error("error IchnoisCore logging")
      IchnoisCore.logger().info("hello %s %s", "there", "world!")
      IchnoisCore.addFeatureFlag("my-feature-flag", true)
      logger.error("Trying person with id={}", id, RuntimeException("error while loading"))
      throw IllegalArgumentException("Something went wrong [id=$id]")
    } finally {
      ichnoisSpan.finish()
    }
  }

  @PostMapping
  open fun create(@RequestBody person: Person): Person {
    val ichnoisSpan = IchnoisCore.getSpan()!!.startChild("spanCreatedThroughIchnoisCoreApi")
    try {
      return personService.create(person)
    } finally {
      ichnoisSpan.finish()
    }
  }
}

@Service
@IchnoisCoreSpan
open class PersonService(private val jdbcTemplate: JdbcTemplate) {
  open fun create(person: Person): Person {
    jdbcTemplate.update(
      "insert into person (firstName, lastName) values (?, ?)",
      person.firstName,
      person.lastName,
    )
    return person
  }
}
