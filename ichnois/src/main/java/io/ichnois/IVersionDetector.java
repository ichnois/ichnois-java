package io.ichnois;

public interface IVersionDetector {

  /**
   * Checks whether all installed IchnoisCore Java SDK dependencies have the same version.
   *
   * @return true if mixed versions have been detected, false if all versions align
   */
  boolean checkForMixedVersions();
}
