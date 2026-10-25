package io.ichnois.spring.jakarta;

import io.ichnois.IchnoisCoreOptions;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables IchnoisCore error handling capabilities.
 *
 * <ul>
 *   <li>creates bean of type {@link io.ichnois.IchnoisCoreOptions}
 *   <li>registers {@link io.ichnois.IScopes} for sending IchnoisCore events
 *   <li>registers {@link IchnoisCoreExceptionResolver} to send IchnoisCore event for any uncaught exception
 *       in Spring MVC flow.
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Import({IchnoisCoreHubRegistrar.class, IchnoisCoreInitBeanPostProcessor.class, IchnoisCoreWebConfiguration.class})
@Target(ElementType.TYPE)
public @interface EnableIchnoisCore {

  /**
   * The DSN tells the SDK where to send the events to. If this value is not provided, the SDK will
   * just not send any events.
   *
   * @return the IchnoisCore DSN
   */
  String dsn() default "";

  /**
   * Whether to send personal identifiable information along with events.
   *
   * @return true if send default PII or false otherwise.
   */
  boolean sendDefaultPii() default false;

  /**
   * Determines whether all web exceptions are reported or only uncaught exceptions.
   *
   * @return the order to use for {@link IchnoisCoreExceptionResolver}
   */
  int exceptionResolverOrder() default 1;

  /**
   * Controls the size of the request body to extract if any. No truncation is done by the SDK. If
   * the request body is larger than the accepted size, nothing is sent.
   */
  IchnoisCoreOptions.RequestSize maxRequestBodySize() default IchnoisCoreOptions.RequestSize.NONE;
}
