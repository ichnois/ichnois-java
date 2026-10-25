package io.ichnois.samples.spring7;

import io.ichnois.spring7.tracing.IchnoisCoreTracingFilter;
import jakarta.servlet.Filter;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class AppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

  @Override
  protected String[] getServletMappings() {
    return new String[] {"/*"};
  }

  @Override
  protected Class<?>[] getRootConfigClasses() {
    return new Class<?>[] {AppConfig.class, SecurityConfiguration.class};
  }

  @Override
  protected Class<?>[] getServletConfigClasses() {
    return new Class<?>[] {WebConfig.class};
  }

  @Override
  protected Filter[] getServletFilters() {
    // creates IchnoisCore transactions around incoming HTTP requests
    IchnoisCoreTracingFilter ichnoisTracingFilter = new IchnoisCoreTracingFilter();

    // filter required by Spring Security
    DelegatingFilterProxy springSecurityFilterChain = new DelegatingFilterProxy();
    springSecurityFilterChain.setTargetBeanName("springSecurityFilterChain");
    // sets request on RequestContextHolder
    RequestContextFilter requestContextFilter = new RequestContextFilter();

    // sets IchnoisCore user on the scope
    DelegatingFilterProxy ichnoisUserFilterProxy = new DelegatingFilterProxy();
    ichnoisUserFilterProxy.setTargetBeanName("ichnoisUserFilter");

    return new Filter[] {
      ichnoisTracingFilter, springSecurityFilterChain, requestContextFilter, ichnoisUserFilterProxy
    };
  }
}
