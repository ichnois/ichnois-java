package io.ichnois.opentelemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;

public final class IchnoisCoreContextStorageProvider implements ContextStorageProvider {
  @Override
  public ContextStorage get() {
    return new IchnoisCoreContextStorage(findStorageToWrap());
  }

  private @NotNull ContextStorage findStorageToWrap() {
    try {
      ServiceLoader<ContextStorageProvider> serviceLoader =
          ServiceLoader.load(ContextStorageProvider.class);
      Iterator<ContextStorageProvider> iterator = serviceLoader.iterator();
      while (iterator.hasNext()) {
        ContextStorageProvider contextStorageProvider = iterator.next();
        if (!(contextStorageProvider instanceof IchnoisCoreContextStorageProvider)) {
          return contextStorageProvider.get();
        }
      }
    } catch (Throwable t) {
      // ignore and use fallback
    }

    // using default / fallback storage
    return new IchnoisCoreOtelThreadLocalStorage();
  }
}
