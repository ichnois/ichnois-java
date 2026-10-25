package io.ichnois.samples.spring.boot4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableCaching
public class IchnoisCoreDemoApplication {
  public static void main(String[] args) {
    SpringApplication.run(IchnoisCoreDemoApplication.class, args);
  }

  @Bean
  WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
