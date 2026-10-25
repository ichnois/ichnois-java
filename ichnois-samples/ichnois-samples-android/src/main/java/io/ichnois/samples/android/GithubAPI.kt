package io.ichnois.samples.android

import io.ichnois.HttpStatusCodeRange
import io.ichnois.okhttp.IchnoisCoreOkHttpEventListener
import io.ichnois.okhttp.IchnoisCoreOkHttpInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GitHubApi {
  private val client =
    OkHttpClient.Builder()
      .eventListener(IchnoisCoreOkHttpEventListener())
      .addInterceptor(
        IchnoisCoreOkHttpInterceptor(
          captureFailedRequests = true,
          failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 599)),
        )
      )
      .build()

  private val retrofit =
    Retrofit.Builder()
      .baseUrl("https://api.github.com/")
      .addConverterFactory(GsonConverterFactory.create())
      .client(client)
      .build()

  val service: GitHubService = retrofit.create(GitHubService::class.java)
}
