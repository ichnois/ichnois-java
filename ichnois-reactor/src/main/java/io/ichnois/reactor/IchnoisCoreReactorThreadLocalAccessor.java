package io.ichnois.reactor;

import io.micrometer.context.ThreadLocalAccessor;
import io.ichnois.IScopes;
import io.ichnois.NoOpScopes;
import io.ichnois.IchnoisCore;

public final class IchnoisCoreReactorThreadLocalAccessor implements ThreadLocalAccessor<IScopes> {

  public static final String KEY = "ichnois-scopes";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public IScopes getValue() {
    return IchnoisCore.getCurrentScopes(false);
  }

  @Override
  public void setValue(IScopes value) {
    IchnoisCore.setCurrentScopes(value);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void reset() {
    IchnoisCore.setCurrentScopes(NoOpScopes.getInstance());
  }
}
