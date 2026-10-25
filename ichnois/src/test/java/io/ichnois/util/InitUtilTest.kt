package io.ichnois.util

import io.ichnois.InitPriority
import io.ichnois.IchnoisCoreOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitUtilTest {
  private var previousOptions: IchnoisCoreOptions? = null
  private var newOptions: IchnoisCoreOptions? = null
  private var clientEnabled: Boolean = true

  @BeforeTest
  fun setup() {
    previousOptions = null
    newOptions = null
    clientEnabled = true
  }

  @Test
  fun `first init on empty options goes through`() {
    givenPreviousOptions(IchnoisCoreOptions.empty())
    givenNewOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenClientDisabled()

    thenInitIsPerformed()
  }

  @Test
  fun `init with same priority goes through`() {
    givenPreviousOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenNewOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenClientEnabled()

    thenInitIsPerformed()
  }

  @Test
  fun `init without previous options goes through`() {
    givenPreviousOptions(null)
    givenNewOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenClientEnabled()

    thenInitIsPerformed()
  }

  @Test
  fun `init with lower priority is ignored if already initialized`() {
    givenPreviousOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOW })
    givenNewOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenClientEnabled()

    thenInitIsIgnored()
  }

  @Test
  fun `init with lower priority goes through if not yet initialized`() {
    givenPreviousOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOW })
    givenNewOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOWEST })
    givenClientDisabled()

    thenInitIsPerformed()
  }

  @Test
  fun `init with lower priority goes through with forceInit if already initialized`() {
    givenPreviousOptions(IchnoisCoreOptions().also { it.initPriority = InitPriority.LOW })
    givenNewOptions(
      IchnoisCoreOptions().also {
        it.initPriority = InitPriority.LOWEST
        it.isForceInit = true
      }
    )
    givenClientEnabled()

    thenInitIsPerformed()
  }

  private fun givenPreviousOptions(options: IchnoisCoreOptions?) {
    previousOptions = options
  }

  private fun givenNewOptions(options: IchnoisCoreOptions?) {
    newOptions = options
  }

  private fun givenClientDisabled() {
    clientEnabled = false
  }

  private fun givenClientEnabled() {
    clientEnabled = true
  }

  private fun thenInitIsPerformed() {
    val shouldInit = InitUtil.shouldInit(previousOptions, newOptions!!, clientEnabled)
    assertTrue(shouldInit)
  }

  private fun thenInitIsIgnored() {
    val shouldInit = InitUtil.shouldInit(previousOptions, newOptions!!, clientEnabled)
    assertFalse(shouldInit)
  }
}
