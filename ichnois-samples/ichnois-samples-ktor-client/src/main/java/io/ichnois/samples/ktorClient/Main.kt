package io.ichnois.samples.ktorClient

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ichnois.HttpStatusCodeRange
import io.ichnois.IchnoisCore
import io.ichnois.TransactionOptions
import io.ichnois.ktorClient.IchnoisCoreKtorClientPlugin
import kotlinx.coroutines.runBlocking

fun main() {
  IchnoisCore.init { options ->
    options.dsn = "https://test-public-key@ichnois-h2jw.onrender.com/5428563"
    options.isDebug = true
    options.isSendDefaultPii = true
    options.tracesSampleRate = 1.0
    options.addInAppInclude("io.ichnois.samples")
    options.isGlobalHubMode = true
  }

  val client =
    HttpClient(Java) {
      install(IchnoisCoreKtorClientPlugin) {
        captureFailedRequests = true
        failedRequestTargets = listOf(".*")
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(500, 599))
      }
    }

  val opts = TransactionOptions().apply { isBindToScope = true }
  val tx = IchnoisCore.startTransaction("My Transaction", "test", opts)

  runBlocking { makeRequests(client) }

  IchnoisCore.captureMessage("Ktor client sample done")
  tx.finish()
}

suspend fun makeRequests(client: HttpClient) {
  // Should create breadcrumbs
  client.get("https://httpbin.org/get")
  client.get("https://httpbin.org/status/404")

  // Should create breadcrumbs and errors
  client.get("https://httpbin.org/status/500?lol=test") // no tags
  IchnoisCore.setTag("lol", "test")
  client.get("https://httpbin.org/status/502") // lol: test
  IchnoisCore.removeTag("lol")
  client.get("https://httpbin.org/status/503?lol=test") // no tags

  // Should create breadcrumb
  client.post("https://httpbin.org/post") {
    setBody("{ \"message\": \"Hello from IchnoisCore Ktor Client!\" }")
    headers {
      append("Content-Type", "application/json")
      append("X-Custom-Header", "IchnoisCore-Ktor-Sample")
    }
  }
}
