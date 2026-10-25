package io.ichnois.kotlin

import io.ichnois.IScopes
import io.ichnois.IchnoisCore
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CopyableThreadContextElement

/** IchnoisCore context element for [CoroutineContext]. */
public class IchnoisCoreContext(private val scopes: IScopes = IchnoisCore.forkedCurrentScope("coroutine")) :
  AbstractCoroutineContextElement(Key), CopyableThreadContextElement<IScopes> {
  private companion object Key : CoroutineContext.Key<IchnoisCoreContext>

  @SuppressWarnings("deprecation")
  override fun copyForChild(): CopyableThreadContextElement<IScopes> =
    IchnoisCoreContext(scopes.forkedCurrentScope("coroutine.child"))

  @SuppressWarnings("deprecation")
  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
    overwritingElement[Key] ?: IchnoisCoreContext(scopes.forkedCurrentScope("coroutine.child"))

  override fun updateThreadContext(context: CoroutineContext): IScopes {
    val oldState = IchnoisCore.getCurrentScopes()
    IchnoisCore.setCurrentScopes(scopes)
    return oldState
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: IScopes) {
    IchnoisCore.setCurrentScopes(oldState)
  }
}
