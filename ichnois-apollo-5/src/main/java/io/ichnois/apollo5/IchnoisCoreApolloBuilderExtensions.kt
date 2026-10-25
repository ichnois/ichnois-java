package io.ichnois.apollo5

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.interceptor.ApolloInterceptor
import io.ichnois.IScopes
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCoreOptions.DEFAULT_PROPAGATION_TARGETS
import io.ichnois.apollo5.IchnoisCoreApollo5HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS

@JvmOverloads
fun ApolloClient.Builder.ichnoisTracing(
  scopes: IScopes = ScopesAdapter.getInstance(),
  captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
  failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
  beforeSpan: IchnoisCoreApollo5HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder {
  addInterceptor(IchnoisCoreApollo5Interceptor(scopes), ApolloInterceptor.InsertionPoint.BeforeCache)
  addHttpInterceptor(
    IchnoisCoreApollo5HttpInterceptor(
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
  beforeSpan: IchnoisCoreApollo5HttpInterceptor.BeforeSpanCallback? = null,
): ApolloClient.Builder =
  ichnoisTracing(
    scopes = ScopesAdapter.getInstance(),
    captureFailedRequests = captureFailedRequests,
    failedRequestTargets = failedRequestTargets,
    beforeSpan = beforeSpan,
  )
