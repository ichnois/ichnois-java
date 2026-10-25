package io.ichnois.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.variables
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import io.ichnois.IScopes
import io.ichnois.ScopesAdapter
import io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor.Companion.ICHNOIS_APOLLO_3_OPERATION_TYPE
import io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor.Companion.ICHNOIS_APOLLO_3_VARIABLES
import io.ichnois.vendor.Base64
import kotlinx.coroutines.flow.Flow

class IchnoisCoreApollo3Interceptor
@JvmOverloads
constructor(private val scopes: IScopes = ScopesAdapter.getInstance()) : ApolloInterceptor {
  override fun <D : Operation.Data> intercept(
    request: ApolloRequest<D>,
    chain: ApolloInterceptorChain,
  ): Flow<ApolloResponse<D>> {
    val builder =
      request
        .newBuilder()
        .addHttpHeader(
          ICHNOIS_APOLLO_3_OPERATION_TYPE,
          Base64.encodeToString(operationType(request).toByteArray(), Base64.NO_WRAP),
        )

    if (scopes.options.dataCollectionResolver.isGraphqlVariablesWithLegacyAlways) {
      request.scalarAdapters?.let {
        builder.addHttpHeader(
          ICHNOIS_APOLLO_3_VARIABLES,
          Base64.encodeToString(
            request.operation.variables(it).valueMap.toString().toByteArray(),
            Base64.NO_WRAP,
          ),
        )
      }
    }
    return chain.proceed(builder.build())
  }
}

private fun <D : Operation.Data> operationType(apolloRequest: ApolloRequest<D>) =
  when (apolloRequest.operation) {
    is Query -> "query"
    is Mutation -> "mutation"
    is Subscription -> "subscription"
    else -> apolloRequest.operation.javaClass.simpleName
  }

private val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
  get() = executionContext[CustomScalarAdapters]
