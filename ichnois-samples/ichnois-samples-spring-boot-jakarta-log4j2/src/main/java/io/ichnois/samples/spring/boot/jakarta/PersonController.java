package io.ichnois.samples.spring.boot.jakarta;

import io.ichnois.ISpan;
import io.ichnois.IchnoisCore;
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
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService) {
    this.personService = personService;
  }

  @GetMapping("{id}")
  Person person(@PathVariable Long id) {
    IchnoisCore.addFeatureFlag("transaction-feature-flag", true);
    ISpan currentSpan = IchnoisCore.getSpan();
    ISpan ichnoisSpan = currentSpan.startChild("spanCreatedThroughIchnoisCoreApi");
    try {
      IchnoisCore.setAttribute("user.type", "admin");
      IchnoisCore.setAttribute("feature.version", 2);
      IchnoisCore.setAttribute("debug.enabled", true);
      IchnoisCore.logger().warn("warn IchnoisCore logging");
      IchnoisCore.logger().error("error IchnoisCore logging");
      IchnoisCore.logger().info("hello %s %s", "there", "world!");
      IchnoisCore.addFeatureFlag("my-feature-flag", true);
      LOGGER.error("Trying person with id={}", id, new RuntimeException("error while loading"));
      throw new IllegalArgumentException("Something went wrong [id=" + id + "]");
    } finally {
      ichnoisSpan.finish();
    }
  }

  @PostMapping
  Person create(@RequestBody Person person) {
    ISpan currentSpan = IchnoisCore.getSpan();
    ISpan ichnoisSpan = currentSpan.startChild("spanCreatedThroughIchnoisCoreApi");
    try {
      return personService.create(person);
    } finally {
      ichnoisSpan.finish();
    }
  }
}
