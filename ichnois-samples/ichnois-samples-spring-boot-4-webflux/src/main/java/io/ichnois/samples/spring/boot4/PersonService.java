package io.ichnois.samples.spring.boot4;

import io.ichnois.IchnoisCore;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class PersonService {

  Mono<Person> create(Person person) {
    return Mono.delay(Duration.ofMillis(100))
        .publishOn(Schedulers.boundedElastic())
        .doOnNext(
            __ -> {
              IchnoisCore.captureMessage("Creating person");
            })
        .map(__ -> person);
  }
}
