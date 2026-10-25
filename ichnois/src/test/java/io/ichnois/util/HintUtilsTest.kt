package io.ichnois.util

import io.ichnois.CustomCachedApplyScopeDataHint
import io.ichnois.Hint
import io.ichnois.hints.ApplyScopeData
import io.ichnois.hints.Backfillable
import io.ichnois.hints.Cached
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class HintUtilsTest {
  @Test
  fun `if event is Cached, it should not apply scopes data`() {
    val hints = HintUtils.createWithTypeCheckHint(mock<Cached>())
    assertFalse(HintUtils.shouldApplyScopeData(hints))
  }

  @Test
  fun `if event is not Cached, it should apply scopes data`() {
    assertTrue(HintUtils.shouldApplyScopeData(Hint()))
  }

  @Test
  fun `if event is ApplyScopeData, it should apply scopes data`() {
    val hints = HintUtils.createWithTypeCheckHint(mock<ApplyScopeData>())
    assertTrue(HintUtils.shouldApplyScopeData(hints))
  }

  @Test
  fun `if event is Cached but also ApplyScopeData, it should apply scopes data`() {
    val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
    assertTrue(HintUtils.shouldApplyScopeData(hints))
  }

  @Test
  fun `if event is Backfillable, it should not apply scopes data`() {
    val hints = HintUtils.createWithTypeCheckHint(mock<Backfillable>())
    assertFalse(HintUtils.shouldApplyScopeData(hints))
  }
}
