package io.ichnois.samples.spring.boot;

import io.ichnois.IchnoisCore;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PersonService {

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .doOnNext(__ -> IchnoisCore.captureMessage("Creating person"))
        .map(__ -> person);
  }
}
