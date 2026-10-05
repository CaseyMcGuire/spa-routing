package com.sparouting.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteAccessHandlers
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.parameter
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import com.sparouting.runtime.rendering.HtmlRenderingOptions
import com.sparouting.runtime.response.RouteHttpResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SinglePageApplicationRoutesTest {
  @Test
  fun `multiple applications serve their own HTML and share the decision endpoint`() = testApplication {
    val requests = mutableListOf<RouteRequest>()
    val configs = listOf("one", "two").map { id ->
      TestConfig(
        id = id,
        routes = listOf(RouteManifest(path = "/$id", id = "Home")),
        evaluateApplication = { request ->
          requests.add(request)
          AccessDecision.Allow
        }
      )
    }
    application { installRoutes(configs) }

    configs.forEach { config ->
      val page = client.get("/${config.id}")
      assertEquals(HttpStatusCode.OK, page.status)
      assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
      assertContains(page.bodyAsText(), "<title>${config.name}</title>")
      assertContains(page.bodyAsText(), "/assets/${config.bundleName}.bundle.js")

      val decision = client.get("/__spa/route-decision?applicationId=${config.id}&routeId=Home")
      assertEquals(RouteHttpResponse.ok(), decision.decision())
    }
    assertEquals(listOf("one", "one", "two", "two"), requests.map { it.applicationId })
    assertEquals(HttpStatusCode.NotFound, client.get("/unknown").status)
  }

  @Test
  fun `custom decision path replaces the default endpoint`() = testApplication {
    val config = TestConfig(routes = listOf(RouteManifest(path = "/app", id = "Home")))
    application {
      installRoutes(listOf(config), routeDecisionPath = "/internal/navigation")
    }

    val decision = client.get("/internal/navigation?applicationId=app&routeId=Home")
    assertEquals(RouteHttpResponse.ok(), decision.decision())
    assertEquals(
      HttpStatusCode.NotFound,
      client.get("/__spa/route-decision?applicationId=app&routeId=Home").status
    )
    assertEquals(HttpStatusCode.OK, client.get("/app").status)
  }

  @Test
  fun `pages and decisions preserve decoded path query and real headers`() = testApplication {
    val requests = mutableListOf<RouteRequest>()
    val config = TestConfig(
      routes = listOf(RouteManifest(
        path = "/app/users/{id}",
        id = "User",
        queryString = listOf(parameter("tag").repeated().optional())
      )),
      evaluateApplication = { request ->
        requests.add(request)
        AccessDecision.Allow
      }
    )
    application { installRoutes(listOf(config)) }

    val page = client.get("/app/users/hello%20world?tag=a%2Bb&tag=%E9%9B%AA&id=ignored") {
      header("X-User", "reader")
      headers.append("X-Group", "one")
      headers.append("X-Group", "two")
    }
    assertEquals(HttpStatusCode.OK, page.status)
    val decision = client.get("/__spa/route-decision") {
      url {
        parameters.append("applicationId", "app")
        parameters.append("routeId", "User")
        parameters.append("parameters.id", "hello world")
        parameters.append("queryString.tag", "a+b")
        parameters.append("queryString.tag", "雪")
        parameters.append("queryString.id", "ignored")
        parameters.append("headers.X-User", "spoofed")
      }
      header("X-User", "reader")
      headers.append("X-Group", "one")
      headers.append("X-Group", "two")
    }
    assertEquals(RouteHttpResponse.ok(), decision.decision())
    assertEquals(2, requests.size)
    requests.forEach { request ->
      assertEquals("app", request.applicationId)
      assertEquals("User", request.routeId)
      assertEquals(mapOf("id" to "hello world"), request.pathParameters)
      assertEquals(mapOf("tag" to listOf("a+b", "雪"), "id" to listOf("ignored")), request.queryString)
      assertEquals(listOf("reader"), request.header("X-User"))
      // The test client's transport combines repeated header lines.
      assertEquals(listOf("one,two"), request.header("X-Group"))
    }
  }

  @Test
  fun `validation and unknown routes return the correct HTTP and decision statuses`() = testApplication {
    val requests = mutableListOf<RouteRequest>()
    val config = TestConfig(
      routes = listOf(RouteManifest(
        path = "/app/users/{id}",
        id = "User",
        queryString = listOf(parameter("q"))
      )),
      evaluateApplication = { request ->
        requests.add(request)
        AccessDecision.Allow
      }
    )
    application {
      installRoutes(listOf(config), invalidQueryStringStatus = 422, invalidPathParameterStatus = 409)
    }

    assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/app/users/1?q=one&q=two").status)
    assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/app/users/1").status)
    val prefix = "/__spa/route-decision?applicationId=app&routeId=User"
    assertEquals(422, client.get("$prefix&parameters.id=1&queryString.q=one&queryString.q=two").decision().statusCode)
    assertEquals(422, client.get("$prefix&parameters.id=1").decision().statusCode)
    assertEquals(409, client.get("$prefix&queryString.q=one").decision().statusCode)
    assertEquals(404, client.get("/__spa/route-decision?applicationId=missing&routeId=User").decision().statusCode)
    assertEquals(404, client.get("/__spa/route-decision?applicationId=app&routeId=missing").decision().statusCode)
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `application and route redirects become page redirects and navigation JSON`() = testApplication {
    val routeChecks = mutableListOf<RouteAccessContext>()
    val loginTarget = RouteTarget(applicationId = "login", routeId = "Home")
    val handler = object : RouteAccessHandler<RouteAccessContext>(
      com.sparouting.contract.Route(applicationId = "app", routeId = "Private")
    ) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision {
        routeChecks.add(request)
        return AccessDecision.Redirect(loginTarget)
      }
    }
    val config = TestConfig(
      routes = listOf(RouteManifest(path = "/app/private", id = "Private", hasAccessHandler = true)),
      handlers = listOf(handler),
      evaluateApplication = { request ->
        if (request.header("X-User").isEmpty()) {
          AccessDecision.Redirect(loginTarget)
        } else {
          AccessDecision.Allow
        }
      }
    )
    val login = TestConfig(id = "login", routes = listOf(RouteManifest(path = "/login", id = "Home")))
    application { installRoutes(listOf(config, login)) }
    val http = createClient { followRedirects = false }

    for (authenticated in listOf(false, true)) {
      val page = http.get("/app/private") {
        if (authenticated) {
          header("X-User", "reader")
        }
      }
      assertEquals(HttpStatusCode.Found, page.status)
      assertEquals("/login", page.headers[HttpHeaders.Location])
      assertEquals("", page.bodyAsText())
      val decision = http.get("/__spa/route-decision?applicationId=app&routeId=Private") {
        if (authenticated) {
          header("X-User", "reader")
        }
      }
      assertEquals(RouteHttpResponse.found("/login"), decision.decision())
      assertEquals(if (authenticated) 2 else 0, routeChecks.size)
    }
    assertEquals(routeChecks[0].path, routeChecks[1].path)
    assertEquals("/app/private", routeChecks[0].path)
  }

  private fun Application.installRoutes(
    configs: List<SinglePageApplicationConfig>,
    invalidQueryStringStatus: Int = 400,
    invalidPathParameterStatus: Int = 400,
    routeDecisionPath: String = "/__spa/route-decision"
  ) {
    install(ContentNegotiation) { jackson() }
    routing {
      singlePageApplicationRoutes(
        configs = configs,
        htmlRenderer = HtmlDocumentRenderer(HtmlRenderingOptions(bundleBasePath = "/assets", globalStylesheet = null)),
        routeDecisionPath = routeDecisionPath,
        invalidPathParameterStatus = invalidPathParameterStatus,
        invalidQueryStringStatus = invalidQueryStringStatus
      )
    }
  }

  private suspend fun HttpResponse.decision(): RouteHttpResponse {
    assertEquals(HttpStatusCode.OK, status)
    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
    assertEquals("no-store", headers[HttpHeaders.CacheControl])
    assertNull(headers[HttpHeaders.Location])
    return jacksonObjectMapper().readValue(bodyAsText())
  }

  private class TestConfig(
    override val id: String = "app",
    override val routes: List<RouteManifest>,
    handlers: List<RouteAccessHandler<*>> = emptyList(),
    evaluateApplication: (RouteRequest) -> AccessDecision = { AccessDecision.Allow }
  ) : SinglePageApplicationConfig {
    override val name: String = "Application $id"
    override val bundleName: String = id
    override val applicationAccessHandler = object : ApplicationAccessHandler<TestConfig>() {
      override fun evaluate(request: RouteRequest): AccessDecision = evaluateApplication(request)
    }
    override val routeAccessHandlers = object : RouteAccessHandlers<TestConfig> {
      override val handlers: List<RouteAccessHandler<*>> = handlers
    }
  }
}
