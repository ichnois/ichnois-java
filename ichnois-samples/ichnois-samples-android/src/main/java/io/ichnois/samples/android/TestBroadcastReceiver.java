package io.ichnois.samples.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * A manifest-declared broadcast receiver for testing standalone app starts.
 *
 * <p>Test with:
 *
 * <pre>{@code
 * adb shell am force-stop io.ichnois.samples.android && \
 * adb shell am broadcast -a io.ichnois.samples.android.TEST_BROADCAST \
 *   -n io.ichnois.samples.android/.TestBroadcastReceiver
 * }</pre>
 */
public class TestBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = "IchnoisCoreAppStart";

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(TAG, "TestBroadcastReceiver.onReceive() called - no activity will launch");
  }
}
