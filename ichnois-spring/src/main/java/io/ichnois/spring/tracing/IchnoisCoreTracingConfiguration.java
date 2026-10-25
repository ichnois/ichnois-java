package io.ichnois.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides infrastructure beans for creating transactions and spans around bean methods annotated
 * with {@link IchnoisCoreTransaction} and {@link IchnoisCoreSpan}.
 */
@Configuration
@Import({
  IchnoisCoreAdviceConfiguration.class,
  IchnoisCoreSpanPointcutConfiguration.class,
  IchnoisCoreTransactionPointcutConfiguration.class
})
@Open
public class IchnoisCoreTracingConfiguration {}
