# ichnois-reactor

This module provides a set of utilities to use IchnoisCore with [Reactor](https://projectreactor.io/).

## Setup

Please refer to the documentation on how to set up our [Java SDK](https://docs.sentry.io/platforms/java/),
or our [Spring](https://docs.sentry.io/platforms/java/guides/spring/) 
or [Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/) integrations if you're using Spring WebFlux.

If you're using our Spring Boot SDK with Spring Boot (`ichnois-spring-boot` or `ichnois-spring-boot-jakarta`), this module will be available and used under the hood to automatically instrument WebFlux.
If you're using our Spring SDK (`ichnois-spring` or `ichnois-spring-jakarta`), you need to configure WebFlux as we do in [IchnoisCoreWebFluxAutoConfiguration](https://github.com/getsentry/sentry-java/blob/a5098280b52aec28c71c150e286b5c937767634d/ichnois-spring-boot-jakarta/src/main/java/io/ichnois/spring/boot/jakarta/IchnoisCoreWebfluxAutoConfiguration.java) for Spring Boot.

Otherwise, read on to find out how to set up and use the integration.

Add the latest version of `io.ichnois.reactor` as a dependency. 
Make sure you're using `io.micrometer:context-propagation:1.0.2` or later, and `io.projectreactor:reactor-core:3.5.3` or later.

Then, enable automatic context propagation:
```java
import reactor.core.publisher.Hooks;
// ...
Hooks.enableAutomaticContextPropagation();
```

## Usage

You can use the utilities provided by this module to wrap `Mono` and `Flux` objects to enable correct errors, breadcrumbs and tracing in your application.

For normal use cases, you should wrap your operations on `Mono` or `Flux` objects using the `withIchnoisCore` function.
This will fork the *current scopes* and use them throughout the stream's execution context.

For example:
```java
import reactor.core.publisher.Mono;
import io.ichnois.IchnoisCore;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.TransactionOptions; 

TransactionOptions txOptions = new TransactionOptions();
txOptions.setBindToScope(true);
ITransaction tx = IchnoisCore.startTransaction("Transaction", "op", txOptions);
ISpan child = tx.startChild("Outside Mono", "op")
IchnoisCore.captureMessage("Message outside Mono")
child.finish()
String result = IchnoisCoreReactorUtils.withIchnoisCore(
  Mono.just("hello")
    .map({ (it) ->
      ISpan span = IchnoisCore.getCurrentScopes().transaction.startChild("Inside Mono", "map");
      IchnoisCore.captureMessage("Message inside Mono");
      span.finish();
      return it;
    })
).block();
System.out.println(result);
tx.finish();
```

For more complex use cases, you can also use `withIchnoisCoreForkedRoots` to fork the root scopes or `withIchnoisCoreScopes` to wrap the operation in arbitrary scopes.

For more information on scopes and scope forking, please consult our [scopes documentation](https://docs.sentry.io/platforms/java/enriching-events/scopes).

Examples of usage of this module (with Spring WebFlux) are provided in 
[ichnois-samples-spring-boot-webflux](https://github.com/getsentry/sentry-java/tree/main/ichnois-samples/ichnois-samples-spring-boot-webflux)
and
[ichnois-samples-spring-boot-webflux-jakarta](https://github.com/getsentry/sentry-java/tree/main/ichnois-samples/ichnois-samples-spring-boot-webflux-jakarta)
.
