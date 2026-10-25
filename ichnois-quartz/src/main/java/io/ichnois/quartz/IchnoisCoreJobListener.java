package io.ichnois.quartz;

import io.ichnois.BuildConfig;
import io.ichnois.CheckIn;
import io.ichnois.CheckInStatus;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.util.LifecycleHelper;
import io.ichnois.util.Objects;
import io.ichnois.util.TracingUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

@ApiStatus.Experimental
public final class IchnoisCoreJobListener implements JobListener {

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-quartz", BuildConfig.VERSION_NAME);
  }

  public static final String ICHNOIS_CHECK_IN_ID_KEY = "ichnois-checkin-id";
  public static final String ICHNOIS_SLUG_KEY = "ichnois-slug";
  public static final String ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY = "ichnois-scope-lifecycle";

  private final @NotNull IScopes scopes;

  public IchnoisCoreJobListener() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreJobListener(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Quartz");
  }

  @Override
  public String getName() {
    return "ichnois-job-listener";
  }

  @Override
  public void jobToBeExecuted(final @NotNull JobExecutionContext context) {
    try {
      final @Nullable String maybeSlug = getSlug(context);
      if (maybeSlug == null) {
        return;
      }
      final @NotNull IIchnoisCoreLifecycleToken lifecycleToken =
          scopes.forkedScopes("IchnoisCoreJobListener").makeCurrent();
      TracingUtils.startNewTrace(scopes);
      final @NotNull String slug = maybeSlug;
      final @NotNull CheckIn checkIn = new CheckIn(slug, CheckInStatus.IN_PROGRESS);
      final @NotNull IchnoisCoreId checkInId = scopes.captureCheckIn(checkIn);
      context.put(ICHNOIS_CHECK_IN_ID_KEY, checkInId);
      context.put(ICHNOIS_SLUG_KEY, slug);
      context.put(ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY, lifecycleToken);
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Unable to capture check-in in jobToBeExecuted.", t);
    }
  }

  private @Nullable String getSlug(final @NotNull JobExecutionContext context) {
    final @Nullable JobDataMap jobDataMap = context.getMergedJobDataMap();
    if (jobDataMap != null) {
      final @Nullable Object o = jobDataMap.get(ICHNOIS_SLUG_KEY);
      if (o != null) {
        return o.toString();
      }
    }

    return null;
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    try {
      final @Nullable Object checkInIdObjectFromContext = context.get(ICHNOIS_CHECK_IN_ID_KEY);
      final @Nullable Object slugObjectFromContext = context.get(ICHNOIS_SLUG_KEY);
      final @NotNull IchnoisCoreId checkInId =
          checkInIdObjectFromContext == null
              ? new IchnoisCoreId()
              : (IchnoisCoreId) checkInIdObjectFromContext;
      final @Nullable String slug =
          slugObjectFromContext == null ? null : (String) slugObjectFromContext;
      if (slug != null) {
        final boolean isFailed = jobException != null;
        final @NotNull CheckInStatus status = isFailed ? CheckInStatus.ERROR : CheckInStatus.OK;
        scopes.captureCheckIn(new CheckIn(checkInId, slug, status));
      }
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Unable to capture check-in in jobWasExecuted.", t);
    } finally {
      LifecycleHelper.close(context.get(ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY));
    }
  }
}
