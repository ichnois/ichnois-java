package io.ichnois.android.core.internal.gestures;

import android.content.Context;
import android.view.MotionEvent;
import android.view.Window;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.SpanStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreWindowCallback extends WindowCallbackAdapter {

  private final @NotNull Window.Callback delegate;
  private final @NotNull IchnoisCoreGestureListener gestureListener;
  private final @NotNull IchnoisCoreGestureDetector gestureDetector;
  private final @Nullable IchnoisCoreOptions options;
  private final @NotNull MotionEventObtainer motionEventObtainer;

  // When we can't be removed from the callback chain (see UserInteractionIntegration),
  // stopTracking() flips this so handleTouchEvent short-circuits.
  private volatile boolean inert;

  public IchnoisCoreWindowCallback(
      final @NotNull Window.Callback delegate,
      final @NotNull Context context,
      final @NotNull IchnoisCoreGestureListener gestureListener,
      final @Nullable IchnoisCoreOptions options) {
    this(
        delegate,
        new IchnoisCoreGestureDetector(context, gestureListener),
        gestureListener,
        options,
        new MotionEventObtainer() {});
  }

  IchnoisCoreWindowCallback(
      final @NotNull Window.Callback delegate,
      final @NotNull IchnoisCoreGestureDetector gestureDetector,
      final @NotNull IchnoisCoreGestureListener gestureListener,
      final @Nullable IchnoisCoreOptions options,
      final @NotNull MotionEventObtainer motionEventObtainer) {
    super(delegate);
    this.delegate = delegate;
    this.gestureListener = gestureListener;
    this.options = options;
    this.gestureDetector = gestureDetector;
    this.motionEventObtainer = motionEventObtainer;
  }

  @Override
  public boolean dispatchTouchEvent(final @Nullable MotionEvent motionEvent) {
    if (motionEvent != null) {
      final MotionEvent copy = motionEventObtainer.obtain(motionEvent);
      try {
        handleTouchEvent(copy);
      } catch (Throwable e) {
        if (options != null) {
          options.getLogger().log(IchnoisCoreLevel.ERROR, "Error dispatching touch event", e);
        }
      } finally {
        copy.recycle();
      }
    }
    return super.dispatchTouchEvent(motionEvent);
  }

  private void handleTouchEvent(final @NotNull MotionEvent motionEvent) {
    if (inert) {
      return;
    }
    gestureDetector.onTouchEvent(motionEvent);
    int action = motionEvent.getActionMasked();
    if (action == MotionEvent.ACTION_UP) {
      gestureListener.onUp(motionEvent);
    }
  }

  public void stopTracking() {
    inert = true;
    gestureListener.stopTracing(SpanStatus.CANCELLED);
    gestureDetector.recycle();
  }

  public @NotNull Window.Callback getDelegate() {
    return delegate;
  }

  interface MotionEventObtainer {
    @NotNull
    default MotionEvent obtain(@NotNull MotionEvent origin) {
      return MotionEvent.obtain(origin);
    }
  }
}
