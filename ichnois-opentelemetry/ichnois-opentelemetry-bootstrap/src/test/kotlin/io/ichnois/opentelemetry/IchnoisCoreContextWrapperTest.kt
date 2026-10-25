package io.ichnois.opentelemetry

import io.opentelemetry.context.Context
import io.ichnois.IchnoisCore
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_SCOPES_KEY
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.test.Test

class IchnoisCoreContextWrapperTest {

  @Test
  fun `ensure noop scopes are not stored on Context`() {
    val context = Context.root()
    assertNull(context.get(ICHNOIS_SCOPES_KEY))
    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)
    val wrappedContext = IchnoisCoreContextWrapper.wrap(context)
    assertNull(wrappedContext.get(ICHNOIS_SCOPES_KEY))
  }
}
