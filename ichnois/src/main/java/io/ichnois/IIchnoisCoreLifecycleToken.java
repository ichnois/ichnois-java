package io.ichnois;

public interface IIchnoisCoreLifecycleToken extends AutoCloseable {

  // overridden to not have a checked exception on the method.
  @Override
  void close();
}
