package io.ichnois

class CustomEventProcessor : EventProcessor {
  override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? = null
}
