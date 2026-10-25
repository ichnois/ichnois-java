package io.ichnois.apollo3

import com.apollographql.apollo3.ApolloClient
import io.ichnois.IScopes
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCoreOptions.DEFAULT_PROPAGATION_TARGETS
import io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.ichnoisTracing(
  scopes: IScopes = ScopesAdapter.getInstance(),
  captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
  failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
  beforeSpan: IchnoisCoreApollo3HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder {
  addInterceptor(IchnoisCoreApollo3Interceptor(scopes))
  addHttpInterceptor(
    IchnoisCoreApollo3HttpInterceptor(
      scopes = scopes,
      captureFailedRequests = captureFailedRequests,
      failedRequestTargets = failedRequestTargets,
      beforeSpan = beforeSpan,
    )
  )
  return this
}

fun ApolloClient.Builder.ichnoisTracing(
  captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
  failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
  beforeSpan: IchnoisCoreApollo3HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder =
  ichnoisTracing(
    scopes = ScopesAdapter.getInstance(),
    captureFailedRequests = captureFailedRequests,
    failedRequestTargets = failedRequestTargets,
    beforeSpan = beforeSpan,
  )
