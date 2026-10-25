package io.ichnois.samples.spring.boot4;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.ichnois.ISpan;
import io.ichnois.IchnoisCore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/person/")
public class PersonController {
  private final PersonService personService;
  private final Tracer tracer;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService, Tracer tracer) {
    this.personService = personService;
    this.tracer = tracer;
  }

  @GetMapping("{id}")
  @WithSpan("personSpanThroughOtelAnnotation")
  Person person(@PathVariable Long id) {
    IchnoisCore.addFeatureFlag("outer-feature-flag", true);
    Span span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan();
    try (final @NotNull Scope spanScope = span.makeCurrent()) {
      IchnoisCore.setAttribute("user.type", "admin");
      IchnoisCore.setAttribute("feature.version", 2);
      IchnoisCore.setAttribute("debug.enabled", true);
      IchnoisCore.logger().warn("warn IchnoisCore logging");
      IchnoisCore.logger().error("error IchnoisCore logging");
      IchnoisCore.logger().info("hello %s %s", "there", "world!");
      IchnoisCore.addFeatureFlag("inner-feature-flag", true);
      ISpan currentSpan = IchnoisCore.getSpan();
      ISpan ichnoisSpan = currentSpan.startChild("spanCreatedThroughIchnoisCoreApi");
      try {
        LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
        throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
      } finally {
        ichnoisSpan.finish();
      }
    } finally {
      span.end();
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    Span span = tracer.spanBuilder("spanCreatedThroughOtelApi").startSpan();
    try (final @NotNull Scope spanScope = span.makeCurrent()) {
      ISpan ichnoisSpan = IchnoisCore.getSpan().startChild("spanCreatedThroughIchnoisCoreApi");
      try {
        return personService.create(person);
      } finally {
        ichnoisSpan.finish();
      }
    } finally {
      span.end();
    }
  }
}
