package io.ichnois;

/**
 * Marker interface for event processors that process events that have to be backfilled, i.e.
 * currently stored in-memory data (like Scope or IchnoisCoreOptions) is irrelevant, because the event
 * happened in the past.
 *
 * <p>SDK-owned implementations must also implement {@link
 * io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor}.
 */
public interface BackfillingEventProcessor extends EventProcessor {}
