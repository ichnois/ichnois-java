package io.ichnois

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IchnoisCoreIntegrationPackageStorageTest {
  @AfterTest
  fun teardown() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `same package version is OK`() {
    val storage = IchnoisCoreIntegrationPackageStorage.getInstance()
    storage.clearStorage()

    storage.addPackage("maven:io.ichnois:ichnois", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois:ichnois-logback", BuildConfig.VERSION_NAME)

    assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
  }

  @Test
  fun `checking twice works`() {
    val storage = IchnoisCoreIntegrationPackageStorage.getInstance()
    storage.clearStorage()

    storage.addPackage("maven:io.ichnois:ichnois", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois:ichnois-logback", BuildConfig.VERSION_NAME)

    assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
    assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
  }

  @Test
  fun `checking twice with changes works`() {
    val storage = IchnoisCoreIntegrationPackageStorage.getInstance()
    storage.clearStorage()

    storage.addPackage("maven:io.ichnois:ichnois", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois:ichnois-logback", BuildConfig.VERSION_NAME)

    assertFalse(storage.checkForMixedVersions(SystemOutLogger()))

    storage.addPackage("maven:io.ichnois:ichnois-spring", "8.0.0")
    assertTrue(storage.checkForMixedVersions(SystemOutLogger()))
  }

  @Test
  fun `mixed package version is not OK`() {
    val storage = IchnoisCoreIntegrationPackageStorage.getInstance()
    storage.clearStorage()

    storage.addPackage("maven:io.ichnois:ichnois", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois:ichnois-logback", "8.0.0")

    assertTrue(storage.checkForMixedVersions(SystemOutLogger()))
  }

  @Test
  fun `only java sdk modules are checked`() {
    val storage = IchnoisCoreIntegrationPackageStorage.getInstance()
    storage.clearStorage()

    storage.addPackage("maven:io.ichnois:ichnois", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois:ichnois-logback", BuildConfig.VERSION_NAME)
    storage.addPackage("maven:io.ichnois.other:ichnois-other", "1.0.0")
    storage.addPackage("maven:io.opentelemetry.javaagent:opentelemetry-javaagent", "2.0.0")

    assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
  }
}
