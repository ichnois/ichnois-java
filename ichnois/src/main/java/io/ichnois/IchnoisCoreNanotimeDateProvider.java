package io.ichnois;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class IchnoisCoreNanotimeDateProvider implements IchnoisCoreDateProvider {

  @Override
  public IchnoisCoreDate now() {
    return new IchnoisCoreNanotimeDate();
  }
}
