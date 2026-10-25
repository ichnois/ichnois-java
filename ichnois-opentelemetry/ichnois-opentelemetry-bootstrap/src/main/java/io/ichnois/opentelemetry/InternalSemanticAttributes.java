package io.ichnois.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InternalSemanticAttributes {
  public static final AttributeKey<Boolean> SAMPLED = AttributeKey.booleanKey("ichnois.sampled");
  public static final AttributeKey<Double> SAMPLE_RATE =
      AttributeKey.doubleKey("ichnois.sample_rate");
  public static final AttributeKey<Double> SAMPLE_RAND =
      AttributeKey.doubleKey("ichnois.sample_rand");
  public static final AttributeKey<Boolean> PARENT_SAMPLED =
      AttributeKey.booleanKey("ichnois.parent_sampled");
  public static final AttributeKey<Boolean> PROFILE_SAMPLED =
      AttributeKey.booleanKey("ichnois.profile_sampled");
  public static final AttributeKey<Double> PROFILE_SAMPLE_RATE =
      AttributeKey.doubleKey("ichnois.profile_sample_rate");
  public static final AttributeKey<Boolean> IS_REMOTE_PARENT =
      AttributeKey.booleanKey("ichnois.is_remote_parent");
  public static final AttributeKey<String> BAGGAGE = AttributeKey.stringKey("ichnois.baggage");
  public static final AttributeKey<Boolean> BAGGAGE_MUTABLE =
      AttributeKey.booleanKey("ichnois.baggage_mutable");
}
