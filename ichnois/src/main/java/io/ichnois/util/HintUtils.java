package io.ichnois.util;

import static io.ichnois.TypeCheckHint.ICHNOIS_CAPTURE_FAILED;
import static io.ichnois.TypeCheckHint.ICHNOIS_DART_SDK_NAME;
import static io.ichnois.TypeCheckHint.ICHNOIS_DOTNET_SDK_NAME;
import static io.ichnois.TypeCheckHint.ICHNOIS_EVENT_DROP_REASON;
import static io.ichnois.TypeCheckHint.ICHNOIS_IS_FROM_HYBRID_SDK;
import static io.ichnois.TypeCheckHint.ICHNOIS_JAVASCRIPT_SDK_NAME;
import static io.ichnois.TypeCheckHint.ICHNOIS_TYPE_CHECK_HINT;

import io.ichnois.Hint;
import io.ichnois.ILogger;
import io.ichnois.hints.ApplyScopeData;
import io.ichnois.hints.Backfillable;
import io.ichnois.hints.Cached;
import io.ichnois.hints.EventDropReason;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Util class dealing with Hint as not to pollute the Hint API with internal functionality */
@ApiStatus.Internal
public final class HintUtils {

  private HintUtils() {}

  public static void setIsFromHybridSdk(final @NotNull Hint hint, final @NotNull String sdkName) {
    if (sdkName.startsWith(ICHNOIS_JAVASCRIPT_SDK_NAME)
        || sdkName.startsWith(ICHNOIS_DART_SDK_NAME)
        || sdkName.startsWith(ICHNOIS_DOTNET_SDK_NAME)) {
      hint.set(ICHNOIS_IS_FROM_HYBRID_SDK, true);
    }
  }

  public static boolean isFromHybridSdk(final @NotNull Hint hint) {
    return Boolean.TRUE.equals(hint.getAs(ICHNOIS_IS_FROM_HYBRID_SDK, Boolean.class));
  }

  public static void setEventDropReason(
      final @NotNull Hint hint, final @NotNull EventDropReason eventDropReason) {
    hint.set(ICHNOIS_EVENT_DROP_REASON, eventDropReason);
  }

  @Nullable
  public static EventDropReason getEventDropReason(final @NotNull Hint hint) {
    return hint.getAs(ICHNOIS_EVENT_DROP_REASON, EventDropReason.class);
  }

  /**
   * Marks the event as not captured because of a technical failure, as opposed to being dropped on
   * purpose. Callers that keep an event around for a later attempt use this to tell the two apart:
   * an empty event id alone does not, because almost every drop also returns one.
   */
  public static void setCaptureFailed(final @Nullable Hint hint) {
    if (hint != null) {
      hint.set(ICHNOIS_CAPTURE_FAILED, true);
    }
  }

  public static boolean isCaptureFailed(final @NotNull Hint hint) {
    return Boolean.TRUE.equals(hint.getAs(ICHNOIS_CAPTURE_FAILED, Boolean.class));
  }

  public static Hint createWithTypeCheckHint(Object typeCheckHint) {
    Hint hint = new Hint();
    setTypeCheckHint(hint, typeCheckHint);
    return hint;
  }

  public static void setTypeCheckHint(@NotNull Hint hint, Object typeCheckHint) {
    hint.set(ICHNOIS_TYPE_CHECK_HINT, typeCheckHint);
  }

  public static @Nullable Object getIchnoisCoreSdkHint(@NotNull Hint hint) {
    return hint.get(ICHNOIS_TYPE_CHECK_HINT);
  }

  public static boolean hasType(@NotNull Hint hint, @NotNull Class<?> clazz) {
    final Object ichnoisSdkHint = getIchnoisCoreSdkHint(hint);
    return clazz.isInstance(ichnoisSdkHint);
  }

  public static <T> void runIfDoesNotHaveType(
      @NotNull Hint hint, @NotNull Class<T> clazz, IchnoisCoreNullableConsumer<Object> lambda) {
    runIfHasType(
        hint,
        clazz,
        (ignored) -> {},
        (value, clazz2) -> {
          lambda.accept(value);
        });
  }

  public static <T> void runIfHasType(
      @NotNull Hint hint, @NotNull Class<T> clazz, IchnoisCoreConsumer<T> lambda) {
    runIfHasType(hint, clazz, lambda, (value, clazz2) -> {});
  }

  public static <T> void runIfHasTypeLogIfNot(
      @NotNull Hint hint, @NotNull Class<T> clazz, ILogger logger, IchnoisCoreConsumer<T> lambda) {
    runIfHasType(
        hint,
        clazz,
        lambda,
        (ichnoisSdkHint, expectedClass) -> {
          LogUtils.logNotInstanceOf(expectedClass, ichnoisSdkHint, logger);
        });
  }

  @SuppressWarnings("unchecked")
  public static <T> void runIfHasType(
      @NotNull Hint hint,
      @NotNull Class<T> clazz,
      IchnoisCoreConsumer<T> lambda,
      IchnoisCoreHintFallback fallbackLambda) {
    Object ichnoisSdkHint = getIchnoisCoreSdkHint(hint);
    if (hasType(hint, clazz) && ichnoisSdkHint != null) {
      lambda.accept((T) ichnoisSdkHint);
    } else {
      fallbackLambda.accept(ichnoisSdkHint, clazz);
    }
  }

  /**
   * Scope's data should be applied if: Hint is of the type ApplyScopeData or Hint is not Cached
   * (this includes a null hint)
   *
   * @return true if it should apply scope's data or false otherwise
   */
  public static boolean shouldApplyScopeData(@NotNull Hint hint) {
    return (!hasType(hint, Cached.class) && !hasType(hint, Backfillable.class))
        || hasType(hint, ApplyScopeData.class);
  }

  @FunctionalInterface
  public interface IchnoisCoreConsumer<T> {
    void accept(@NotNull T t);
  }

  @FunctionalInterface
  public interface IchnoisCoreNullableConsumer<T> {
    void accept(@Nullable T t);
  }

  @FunctionalInterface
  public interface IchnoisCoreHintFallback {
    void accept(@Nullable Object ichnoisSdkHint, @NotNull Class<?> clazz);
  }
}
