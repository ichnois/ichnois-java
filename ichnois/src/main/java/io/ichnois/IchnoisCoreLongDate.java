package io.ichnois;

public final class IchnoisCoreLongDate extends IchnoisCoreDate {
  private final long nanos;

  public IchnoisCoreLongDate(final long nanos) {
    this.nanos = nanos;
  }

  @Override
  public long nanoTimestamp() {
    return nanos;
  }
}
