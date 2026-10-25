package io.ichnois.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.spring7.IchnoisCoreProfilerConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
    name = {
      "io.ichnois.opentelemetry.agent.AgentMarker",
      "io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler"
    })
@Open
@Import(IchnoisCoreProfilerConfiguration.class)
public class IchnoisCoreProfilerAutoConfiguration {}
