package io.ichnois.samples.spring.boot.jakarta;

import io.ichnois.ISpan;
import io.ichnois.IchnoisCore;
import io.ichnois.spring.jakarta.tracing.IchnoisCoreSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * {@link IchnoisCoreSpan} can be added either on the class or the method to create spans around method
 * executions.
 */
@Service
@IchnoisCoreSpan
public class PersonService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonService.class);

  private final JdbcTemplate jdbcTemplate;
  private int createCount = 0;

  public PersonService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  Person create(Person person) {
    createCount++;
    final ISpan span = IchnoisCore.getSpan();
    if (span != null) {
      span.setMeasurement("create_count", createCount);
    }

    jdbcTemplate.update(
        "insert into person (firstName, lastName) values (?, ?)",
        person.getFirstName(),
        person.getLastName());

    return person;
  }
}
