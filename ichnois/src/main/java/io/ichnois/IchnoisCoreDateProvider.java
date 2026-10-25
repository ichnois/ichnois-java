package io.ichnois;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface IchnoisCoreDateProvider {
  IchnoisCoreDate now();
}
