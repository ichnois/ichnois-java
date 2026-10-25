package io.ichnois;

import io.ichnois.util.UUIDGenerator;
import io.ichnois.util.UUIDStringUtils;

/**
 * IchnoisCoreUUID is a utility class for generating IchnoisCore-specific ID Strings. It provides methods for
 * generating IchnoisCore IDs and IchnoisCore Span IDs.
 */
public final class IchnoisCoreUUID {

  private IchnoisCoreUUID() {
    // A private constructor prevents callers from accidentally instantiating FastUUID objects
  }

  public static String generateIchnoisCoreId() {
    return UUIDStringUtils.toIchnoisCoreIdString(UUIDGenerator.randomUUID());
  }

  public static String generateSpanId() {
    return UUIDStringUtils.toIchnoisCoreSpanIdString(UUIDGenerator.randomHalfLengthUUID());
  }
}
