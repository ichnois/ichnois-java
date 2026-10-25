package io.ichnois.samples.netflix.dgs;

import com.netflix.graphql.dgs.exceptions.DefaultDataFetcherExceptionHandler;
import io.ichnois.graphql.IchnoisCoreGenericDataFetcherExceptionHandler;
import io.ichnois.graphql.IchnoisCoreInstrumentation;
import io.ichnois.spring.graphql.IchnoisCoreDgsSubscriptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NetlixDgsApplication {

  public static void main(String[] args) {
    SpringApplication.run(NetlixDgsApplication.class, args);
  }

  @Bean
  IchnoisCoreInstrumentation ichnoisInstrumentation() {
    return new IchnoisCoreInstrumentation(new IchnoisCoreDgsSubscriptionHandler(), true);
  }

  @Bean
  IchnoisCoreGenericDataFetcherExceptionHandler ichnoisDataFetcherExceptionHandler() {
    // delegate to default Netflix DGS exception handler
    return new IchnoisCoreGenericDataFetcherExceptionHandler(new DefaultDataFetcherExceptionHandler());
  }
}
