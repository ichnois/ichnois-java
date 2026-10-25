package io.ichnois.spring;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.InitPriority;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.spring.tracing.SpringMvcTransactionNameProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/** Registers beans required to use IchnoisCore core features. */
@Configuration
@Open
public class IchnoisCoreHubRegistrar implements ImportBeanDefinitionRegistrar {

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-spring", BuildConfig.VERSION_NAME);
  }

  @Override
  public void registerBeanDefinitions(
      final @NotNull AnnotationMetadata importingClassMetadata,
      final @NotNull BeanDefinitionRegistry registry) {
    final AnnotationAttributes annotationAttributes =
        AnnotationAttributes.fromMap(
            importingClassMetadata.getAnnotationAttributes(EnableIchnoisCore.class.getName()));
    if (annotationAttributes != null && annotationAttributes.containsKey("dsn")) {
      registerIchnoisCoreOptions(registry, annotationAttributes);
      registerIchnoisCoreHubBean(registry);
      registerIchnoisCoreExceptionResolver(registry, annotationAttributes);
    }
  }

  private void registerIchnoisCoreOptions(
      final @NotNull BeanDefinitionRegistry registry,
      final @NotNull AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(IchnoisCoreOptions.class);

    if (registry.containsBeanDefinition("mockTransportFactory")) {
      builder.addPropertyReference("transportFactory", "mockTransportFactory");
    }
    builder.addPropertyValue("dsn", annotationAttributes.getString("dsn"));
    builder.addPropertyValue("enableExternalConfiguration", true);
    builder.addPropertyValue("ichnoisClientName", BuildConfig.ICHNOIS_SPRING_SDK_NAME);
    builder.addPropertyValue("sdkVersion", createSdkVersion());
    builder.addPropertyValue("initPriority", InitPriority.LOW);
    addPackageAndIntegrationInfo();
    if (annotationAttributes.containsKey("sendDefaultPii")) {
      builder.addPropertyValue("sendDefaultPii", annotationAttributes.getBoolean("sendDefaultPii"));
    }
    if (annotationAttributes.containsKey("maxRequestBodySize")) {
      builder.addPropertyValue(
          "maxRequestBodySize", annotationAttributes.get("maxRequestBodySize"));
    }

    registry.registerBeanDefinition("ichnoisOptions", builder.getBeanDefinition());
  }

  private void registerIchnoisCoreHubBean(final @NotNull BeanDefinitionRegistry registry) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(ScopesAdapter.class);
    builder.setInitMethodName("getInstance");

    registry.registerBeanDefinition("ichnoisHub", builder.getBeanDefinition());
  }

  private void registerIchnoisCoreExceptionResolver(
      final @NotNull BeanDefinitionRegistry registry,
      final @NotNull AnnotationAttributes annotationAttributes) {
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(IchnoisCoreExceptionResolver.class);
    builder.addConstructorArgReference("ichnoisHub");
    builder.addConstructorArgValue(new SpringMvcTransactionNameProvider());
    int order = annotationAttributes.getNumber("exceptionResolverOrder");
    builder.addConstructorArgValue(order);

    registry.registerBeanDefinition("ichnoisExceptionResolver", builder.getBeanDefinition());
  }

  private static @NotNull SdkVersion createSdkVersion() {
    final IchnoisCoreOptions defaultOptions = new IchnoisCoreOptions();
    SdkVersion sdkVersion = defaultOptions.getSdkVersion();

    final String name = BuildConfig.ICHNOIS_SPRING_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private static void addPackageAndIntegrationInfo() {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Spring");
  }
}
