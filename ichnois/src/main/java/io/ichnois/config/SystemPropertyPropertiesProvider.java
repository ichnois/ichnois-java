package io.ichnois.config;

/**
 * {@link PropertiesProvider} implementation that resolves properties from Java system properties.
 */
final class SystemPropertyPropertiesProvider extends AbstractPropertiesProvider {
  private static final String PREFIX = "ichnois.";

  public SystemPropertyPropertiesProvider() {
    super(PREFIX, System.getProperties());
  }
}
