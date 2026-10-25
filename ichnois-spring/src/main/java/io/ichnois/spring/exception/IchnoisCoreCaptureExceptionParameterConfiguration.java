package io.ichnois.spring.exception;

import com.jakewharton.nopen.annotation.Open;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides infrastructure beans for capturing exceptions passed to bean methods annotated with
 * {@link IchnoisCoreCaptureExceptionParameter}.
 */
@Configuration
@Import({
  IchnoisCoreExceptionParameterAdviceConfiguration.class,
  IchnoisCoreCaptureExceptionParameterPointcutConfiguration.class
})
@Open
public class IchnoisCoreCaptureExceptionParameterConfiguration {}
