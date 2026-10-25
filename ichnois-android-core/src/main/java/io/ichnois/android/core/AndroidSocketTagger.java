package io.ichnois.android.core;

import android.net.TrafficStats;
import io.ichnois.ISocketTagger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AndroidSocketTagger implements ISocketTagger {

  // just a random number to tag outgoing traffic from the IchnoisCore SDK
  private static final int ICHNOIS_TAG = 0xF001;

  private static final AndroidSocketTagger instance = new AndroidSocketTagger();

  private AndroidSocketTagger() {}

  public static AndroidSocketTagger getInstance() {
    return instance;
  }

  @Override
  public void tagSockets() {
    TrafficStats.setThreadStatsTag(ICHNOIS_TAG);
  }

  @Override
  public void untagSockets() {
    TrafficStats.clearThreadStatsTag();
  }
}
