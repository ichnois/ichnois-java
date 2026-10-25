package io.ichnois.cache;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreEnvelope;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeCache extends Iterable<IchnoisCoreEnvelope> {

  @Deprecated
  void store(@NotNull IchnoisCoreEnvelope envelope, @NotNull Hint hint);

  default boolean storeEnvelope(@NotNull IchnoisCoreEnvelope envelope, @NotNull Hint hint) {
    store(envelope, hint);
    return true;
  }

  @Deprecated
  default void store(@NotNull IchnoisCoreEnvelope envelope) {
    storeEnvelope(envelope, new Hint());
  }

  void discard(@NotNull IchnoisCoreEnvelope envelope);
}
