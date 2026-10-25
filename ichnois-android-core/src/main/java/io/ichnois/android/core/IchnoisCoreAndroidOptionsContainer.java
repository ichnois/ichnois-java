package io.ichnois.android.core;

import io.ichnois.OptionsContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Direct OptionsContainer for IchnoisCoreAndroidOptions that avoids reflective
 * getDeclaredConstructor().newInstance() on the Android startup path.
 */
final class IchnoisCoreAndroidOptionsContainer extends OptionsContainer<IchnoisCoreAndroidOptions> {

  @Override
  public @NotNull IchnoisCoreAndroidOptions createInstance() {
    return new IchnoisCoreAndroidOptions();
  }
}
