package io.ichnois.transport;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.cache.IEnvelopeCache;
import java.util.Collections;
import java.util.Iterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpEnvelopeCache implements IEnvelopeCache {
  private static final NoOpEnvelopeCache instance = new NoOpEnvelopeCache();

  public static NoOpEnvelopeCache getInstance() {
    return instance;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void store(@NotNull IchnoisCoreEnvelope envelope, @NotNull Hint hint) {}

  @Override
  public boolean storeEnvelope(@NotNull IchnoisCoreEnvelope envelope, @NotNull Hint hint) {
    return false;
  }

  @Override
  public void discard(@NotNull IchnoisCoreEnvelope envelope) {}

  @NotNull
  @Override
  public Iterator<IchnoisCoreEnvelope> iterator() {
    return Collections.emptyIterator();
  }
}
