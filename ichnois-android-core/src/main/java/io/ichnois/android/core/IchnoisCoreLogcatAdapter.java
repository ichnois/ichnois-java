package io.ichnois.android.core;

import android.util.Log;
import io.ichnois.Breadcrumb;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLogLevel;
import io.ichnois.logger.IchnoisCoreLogParameters;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class replaces {@link android.util.Log} with its own implementations which creates a {@link
 * io.ichnois.Breadcrumb} for each log. It only replaces log functions that meet a minimum level set
 * by the user on the IchnoisCore Android Gradle Plugin.
 */
@ApiStatus.Internal
public final class IchnoisCoreLogcatAdapter {

  private static void addAsBreadcrumb(
      @Nullable String tag, @NotNull IchnoisCoreLevel level, @Nullable String msg) {
    addAsBreadcrumb(tag, level, msg, null);
  }

  private static void addAsBreadcrumb(
      @Nullable String tag, @NotNull IchnoisCoreLevel level, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, level, null, tr);
  }

  private static void addAsBreadcrumb(
      @Nullable final String tag,
      @NotNull final IchnoisCoreLevel level,
      @Nullable final String msg,
      @Nullable final Throwable tr) {
    Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setCategory("Logcat");
    breadcrumb.setMessage(msg);
    breadcrumb.setLevel(level);
    if (tag != null) {
      breadcrumb.setData("tag", tag);
    }
    if (tr != null && tr.getMessage() != null) {
      breadcrumb.setData("throwable", tr.getMessage());
    }
    IchnoisCore.addBreadcrumb(breadcrumb);
  }

  private static void addAsLog(
      @NotNull final IchnoisCoreLogLevel level,
      @Nullable final String msg,
      @Nullable final Throwable tr) {
    final @NotNull ScopesAdapter scopes = ScopesAdapter.getInstance();
    // Check if logs are enabled before doing expensive operations
    if (!scopes.getOptions().getLogs().isEnabled()) {
      return;
    }
    final @Nullable String trMessage = tr != null ? tr.getMessage() : null;
    final @NotNull IchnoisCoreLogParameters params = new IchnoisCoreLogParameters();
    params.setOrigin("auto.log.logcat");

    if (tr == null || trMessage == null) {
      scopes.logger().log(level, params, msg);
    } else {
      scopes.logger().log(level, params, msg != null ? (msg + "\n" + trMessage) : trMessage);
    }
  }

  public static int v(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.DEBUG, msg);
    addAsLog(IchnoisCoreLogLevel.TRACE, msg, null);
    return Log.v(tag, msg);
  }

  public static int v(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.DEBUG, msg, tr);
    addAsLog(IchnoisCoreLogLevel.TRACE, msg, tr);
    return Log.v(tag, msg, tr);
  }

  public static int d(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.DEBUG, msg);
    addAsLog(IchnoisCoreLogLevel.DEBUG, msg, null);
    return Log.d(tag, msg);
  }

  public static int d(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.DEBUG, msg, tr);
    addAsLog(IchnoisCoreLogLevel.DEBUG, msg, tr);
    return Log.d(tag, msg, tr);
  }

  public static int i(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.INFO, msg);
    addAsLog(IchnoisCoreLogLevel.INFO, msg, null);
    return Log.i(tag, msg);
  }

  public static int i(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.INFO, msg, tr);
    addAsLog(IchnoisCoreLogLevel.INFO, msg, tr);
    return Log.i(tag, msg, tr);
  }

  public static int w(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.WARNING, msg);
    addAsLog(IchnoisCoreLogLevel.WARN, msg, null);
    return Log.w(tag, msg);
  }

  public static int w(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.WARNING, msg, tr);
    addAsLog(IchnoisCoreLogLevel.WARN, msg, tr);
    return Log.w(tag, msg, tr);
  }

  public static int w(@Nullable String tag, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.WARNING, tr);
    addAsLog(IchnoisCoreLogLevel.WARN, null, tr);
    return Log.w(tag, tr);
  }

  public static int e(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.ERROR, msg);
    addAsLog(IchnoisCoreLogLevel.ERROR, msg, null);
    return Log.e(tag, msg);
  }

  public static int e(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.ERROR, msg, tr);
    addAsLog(IchnoisCoreLogLevel.ERROR, msg, tr);
    return Log.e(tag, msg, tr);
  }

  public static int wtf(@Nullable String tag, @Nullable String msg) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.ERROR, msg);
    addAsLog(IchnoisCoreLogLevel.FATAL, msg, null);
    return Log.wtf(tag, msg);
  }

  public static int wtf(@Nullable String tag, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.ERROR, tr);
    addAsLog(IchnoisCoreLogLevel.FATAL, null, tr);
    return Log.wtf(tag, tr);
  }

  public static int wtf(@Nullable String tag, @Nullable String msg, @Nullable Throwable tr) {
    addAsBreadcrumb(tag, IchnoisCoreLevel.ERROR, msg, tr);
    addAsLog(IchnoisCoreLogLevel.FATAL, msg, tr);
    return Log.wtf(tag, msg, tr);
  }
}
