package io.ichnois.spring.checkin;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.CheckIn;
import io.ichnois.CheckInStatus;
import io.ichnois.DateUtils;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.time.Stopwatch;
import io.ichnois.util.Objects;
import io.ichnois.util.TracingUtils;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringValueResolver;

/**
 * Reports execution of every bean method annotated with {@link IchnoisCoreCheckIn} as a monitor
 * check-in.
 */
@ApiStatus.Internal
@Open
public class IchnoisCoreCheckInAdvice implements MethodInterceptor, EmbeddedValueResolverAware {
  private final @NotNull IScopes scopes;

  private @Nullable StringValueResolver resolver;

  public IchnoisCoreCheckInAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreCheckInAdvice(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    @Nullable
    IchnoisCoreCheckIn checkInAnnotation =
        AnnotationUtils.findAnnotation(mostSpecificMethod, IchnoisCoreCheckIn.class);
    if (checkInAnnotation == null) {
      return invocation.proceed();
    }

    final boolean isHeartbeatOnly = checkInAnnotation.heartbeat();

    @Nullable String monitorSlug = checkInAnnotation.value();

    if (resolver != null) {
      try {
        monitorSlug = resolver.resolveStringValue(checkInAnnotation.value());
      } catch (Throwable e) {
        // When resolving fails, we fall back to the original string which may contain unresolved
        // expressions.
        // Testing shows this can also happen if properties cannot be resolved (without an exception
        // being thrown).
        // IchnoisCore should alert the user about missed checkins in this case since the monitor slug
        // won't match
        // what is configured in IchnoisCore.
        scopes
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Slug for method annotated with @IchnoisCoreCheckIn could not be resolved from properties.",
                e);
      }
    }

    if (ObjectUtils.isEmpty(monitorSlug)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Not capturing check-in for method annotated with @IchnoisCoreCheckIn because it does not specify a monitor slug.");
      return invocation.proceed();
    }

    try (final @NotNull IIchnoisCoreLifecycleToken ignored =
            scopes.forkedScopes("IchnoisCoreCheckInAdvice").makeCurrent()) {
      TracingUtils.startNewTrace(scopes);

      @Nullable IchnoisCoreId checkInId = null;
      final @NotNull Stopwatch stopwatch =
          Stopwatch.started(scopes.getOptions().getMonotonicTicker());
      boolean didError = false;

      try {
        if (!isHeartbeatOnly) {
          checkInId = scopes.captureCheckIn(new CheckIn(monitorSlug, CheckInStatus.IN_PROGRESS));
        }
        return invocation.proceed();
      } catch (Throwable e) {
        didError = true;
        throw e;
      } finally {
        final @NotNull CheckInStatus status = didError ? CheckInStatus.ERROR : CheckInStatus.OK;
        CheckIn checkIn = new CheckIn(checkInId, monitorSlug, status);
        checkIn.setDuration(DateUtils.nanosToSeconds(stopwatch.elapsedNanos()));
        scopes.captureCheckIn(checkIn);
      }
    }
  }

  @Override
  public void setEmbeddedValueResolver(StringValueResolver resolver) {
    this.resolver = resolver;
  }
}
