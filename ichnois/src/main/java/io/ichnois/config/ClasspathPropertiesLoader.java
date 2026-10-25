package io.ichnois.config;

import static io.ichnois.util.ClassLoaderUtils.classLoaderOrDefault;

import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Loads {@link Properties} from the `ichnois.properties` file located on the classpath. */
final class ClasspathPropertiesLoader implements PropertiesLoader {
  private final @NotNull String fileName;
  private final @NotNull ClassLoader classLoader;
  private final @NotNull ILogger logger;

  public ClasspathPropertiesLoader(
      @NotNull String fileName, @Nullable ClassLoader classLoader, @NotNull ILogger logger) {
    this.fileName = fileName;
    this.classLoader = classLoaderOrDefault(classLoader);
    this.logger = logger;
  }

  public ClasspathPropertiesLoader(@NotNull ILogger logger) {
    this("ichnois.properties", ClasspathPropertiesLoader.class.getClassLoader(), logger);
  }

  @Override
  public @Nullable Properties load() {
    try (InputStream inputStream = classLoader.getResourceAsStream(fileName)) {
      if (inputStream != null) {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
          final Properties properties = new Properties();
          properties.load(bufferedInputStream);
          return properties;
        }
      }
    } catch (IOException e) {
      logger.log(
          IchnoisCoreLevel.ERROR,
          e,
          "Failed to load IchnoisCore configuration from classpath resource: %s",
          fileName);
      return null;
    }
    return null;
  }
}
