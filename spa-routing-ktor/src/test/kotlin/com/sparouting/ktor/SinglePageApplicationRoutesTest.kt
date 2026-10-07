package com.sparouting.ktor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.sparouting.runtime.evaluation.DefaultRouteFailureHandler
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.contract.AccessDecision
import com.sparouting.contract.ApplicationAccessHandler
import com.sparouting.contract.HtmlRenderer
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteAccessHandlers
import com.sparouting.contract.RouteManifest
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.contract.parameter
import com.sparouting.runtime.rendering.HtmlDocumentRenderer
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.DefaultRouteHttpResponseConverter
import com.sparouting.runtime.response.RouteHttpResponse
import com.sparouting.runtime.response.RouteHttpResponseConverter
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
  fun `each application renders with its own configured renderer only on page loads`() = testApplication {
    val rendered = mutableListOf<String>()
    val configs = listOf("one", "two").map { id ->
      TestConfig(
        id = id,
        routes = listOf(RouteManifest(path = "/$id", id = "Home")),
        htmlRenderer = HtmlRenderer { application ->
          rendered.add(application.id)
          "<h1>$id: ${application.name}</h1>"
        }
      )
    }
    application { installRoutes(configs) }

    configs.forEach { config ->
      val page = client.get("/${config.id}")
      assertEquals(HttpStatusCode.OK, page.status)
      assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
      assertEquals("<h1>${config.id}: ${config.name}</h1>", page.bodyAsText())
      assertEquals(
        jsonResult(RouteResult.Allowed),
        client.get("/__spa/route-decision?applicationId=${config.id}&routeId=Home").decision()
      )
    }
    assertEquals(listOf("one", "two"), rendered)
  }

  @Test
  fun `multiple applications serve their own HTML and share the decision endpoint`() = testApplication {
    val requests = mutableListOf<RouteRequest>()
    val configs = listOf("one", "two").map { id ->
      TestConfig(
        id = id,
        routes = listOf(RouteManifest(path = "/$id", id = "Home")),
        evaluateApplication = { request ->
          requests.add(request)
          AccessDecision.Allowed
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
      assertEquals(jsonResult(RouteResult.Allowed), decision.decision())
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
    assertEquals(jsonResult(RouteResult.Allowed), decision.decision())
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
        AccessDecision.Allowed
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
    assertEquals(jsonResult(RouteResult.Allowed), decision.decision())
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
  fun `validation and lookup failures always carry a recovery destination`() = testApplication {
    val config = TestConfig(
      routes = listOf(RouteManifest("/app/users/{id}", "User", queryString = listOf(parameter("q")))),
      htmlRenderer = HtmlRenderer { error("Invalid requests must not render HTML") },
      evaluateApplication = { error("Invalid requests must not reach access handlers") }
    )
    application { installRoutes(listOf(config)) }
    val http = createClient { followRedirects = false }
    for (path in listOf("/app/users/1?q=one&q=two", "/app/users/1")) {
      val page = http.get(path)
      assertEquals(HttpStatusCode.Found, page.status)
      assertEquals("/errors/invalid-request", page.headers[HttpHeaders.Location])
      assertEquals("", page.bodyAsText())
    }
    val prefix = "/__spa/route-decision?applicationId=app&routeId=User"
    for (query in listOf("&parameters.id=1&queryString.q=one&queryString.q=two", "&parameters.id=1", "&queryString.q=one")) {
      val decision = http.get(prefix + query).decision()
      assertEquals("invalid_request", decision["type"].asText())
      assertEquals("/errors/invalid-request", decision["destination"].asText())
    }
    for (query in listOf("applicationId=missing&routeId=User", "applicationId=app&routeId=missing")) {
      val decision = http.get("/__spa/route-decision?$query").decision()
      assertEquals("unknown_route", decision["type"].asText())
      assertEquals("/errors/not-found", decision["destination"].asText())
    }
  }

  @Test
  fun `a validation status of 200 does not render an invalid page`() = testApplication {
    val config = TestConfig(
      routes = listOf(RouteManifest(path = "/app", id = "Home", queryString = listOf(parameter("q")))),
      evaluateApplication = { error("Invalid requests must not reach access handlers") },
      htmlRenderer = HtmlRenderer { error("Only Allowed may render HTML") }
    )
    application {
      installRoutes(listOf(config), responseConverter = RouteHttpResponseConverter { _, _ -> RouteHttpResponse(200) })
    }

    val page = client.get("/app")
    assertEquals(HttpStatusCode.OK, page.status)
    assertEquals("", page.bodyAsText())
    val decision = client.get("/__spa/route-decision?applicationId=app&routeId=Home").decision()
    assertEquals("invalid_request", decision["type"].asText())
    assertEquals("/errors/invalid-request", decision["destination"].asText())
  }

  @Test
  fun `application and route denials become page redirects and navigation destinations`() = testApplication {
    val routeChecks = mutableListOf<RouteAccessContext>()
    val loginTarget = RouteTarget(applicationId = "login", routeId = "Home")
    val handler = object : RouteAccessHandler<RouteAccessContext>(
      com.sparouting.contract.Route(applicationId = "app", routeId = "Private")
    ) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context

      override fun evaluate(request: RouteAccessContext): AccessDecision {
        routeChecks.add(request)
        return AccessDecision.Denied(destination = loginTarget)
      }
    }
    val config = TestConfig(
      routes = listOf(RouteManifest(path = "/app/private", id = "Private", hasAccessHandler = true)),
      handlers = listOf(handler),
      htmlRenderer = HtmlRenderer { error("Denied pages must not render HTML") },
      evaluateApplication = { request ->
        if (request.header("X-User").isEmpty()) {
          AccessDecision.Denied(destination = loginTarget)
        } else {
          AccessDecision.Allowed
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
      assertEquals(
        jsonResult(RouteResult.Denied(destination = "/login")),
        decision.decision()
      )
      assertEquals(if (authenticated) 2 else 0, routeChecks.size)
    }
    assertEquals(routeChecks[0].path, routeChecks[1].path)
    assertEquals("/app/private", routeChecks[0].path)
  }

  @Test
  fun `custom failure handler supplies the same recovery outcome for pages and navigation`() = testApplication {
    val requests = mutableListOf<RouteRequest>()
    val handler = object : RouteFailureHandler {
      override fun unknownRoute(request: RouteRequest): AccessDecision.Denied = defaultFailureHandler.unknownRoute(request)
      override fun invalidRequest(request: RouteRequest): AccessDecision.Denied {
        requests.add(request)
        return AccessDecision.Denied(destination = RouteTarget("app", "Home"))
      }
    }
    val config = TestConfig(routes = listOf(
      RouteManifest("/app/users/{id}", "User", queryString = listOf(parameter("q"))),
      RouteManifest("/home", "Home")
    ))
    application { installRoutes(listOf(config), failureHandler = handler) }
    val http = createClient { followRedirects = false }
    val page = http.get("/app/users/42?q=one&q=two") { header("X-User", "reader") }
    assertEquals(HttpStatusCode.Found, page.status)
    assertEquals("/home", page.headers[HttpHeaders.Location])
    assertEquals("", page.bodyAsText())
    val decision = http.get("/__spa/route-decision") {
      url {
        parameters.append("applicationId", "app")
        parameters.append("routeId", "User")
        parameters.append("parameters.id", "42")
        parameters.append("queryString.q", "one")
        parameters.append("queryString.q", "two")
      }
      header("X-User", "reader")
    }
    assertEquals(jsonResult(RouteResult.InvalidRequest(destination = "/home")), decision.decision())
    assertEquals(2, requests.size)
    requests.forEach { request ->
      assertEquals("app", request.applicationId)
      assertEquals("User", request.routeId)
      assertEquals(mapOf("id" to "42"), request.pathParameters)
      assertEquals(mapOf("q" to listOf("one", "two")), request.queryString)
      assertEquals(listOf("reader"), request.header("X-User"))
    }
    assertEquals(HttpStatusCode.OK, http.get("/home").status)
  }

  @Test
  fun `custom responses suppress HTML when an allowed route is mapped to a redirect or error`() {
    for (response in listOf(RouteHttpResponse(303, "/elsewhere"), RouteHttpResponse(403))) {
      testApplication {
        val config = TestConfig(
          routes = listOf(RouteManifest("/app", "Home")),
          htmlRenderer = HtmlRenderer { error("Overridden responses must not render HTML") }
        )
        application {
          installRoutes(listOf(config), responseConverter = RouteHttpResponseConverter { _, result ->
            assertEquals(RouteResult.Allowed, result)
            response
          })
        }
        assertEquals(
          jsonResult(RouteResult.Allowed),
          client.get("/__spa/route-decision?applicationId=app&routeId=Home").decision()
        )
        val page = createClient { followRedirects = false }.get("/app")
        assertEquals(response.statusCode, page.status.value)
        assertEquals(response.location, page.headers[HttpHeaders.Location])
        assertEquals("", page.bodyAsText())
      }
    }
  }

  private fun Application.installRoutes(
    configs: List<SinglePageApplicationConfig>,
    failureHandler: RouteFailureHandler = defaultFailureHandler,
    routeDecisionPath: String = "/__spa/route-decision",
    responseConverter: RouteHttpResponseConverter = DefaultRouteHttpResponseConverter()
  ) {
    install(ContentNegotiation) { jackson() }
    routing {
      singlePageApplicationRoutes(
        configs = configs + TestConfig(id = "errors", routes = listOf(
          RouteManifest("/errors/not-found", "NotFound"), RouteManifest("/errors/invalid-request", "Invalid")
        )),
        routeDecisionPath = routeDecisionPath,
        failureHandler = failureHandler,
        responseConverter = responseConverter
      )
    }
  }

  private suspend fun HttpResponse.decision(): JsonNode {
    assertEquals(HttpStatusCode.OK, status)
    assertEquals(ContentType.Application.Json, contentType()?.withoutParameters())
    assertEquals("no-store", headers[HttpHeaders.CacheControl])
    assertNull(headers[HttpHeaders.Location])
    val body = jacksonObjectMapper().readTree(bodyAsText())
    assertTrue(body["type"].asText() in listOf("allowed", "denied", "unknown_route", "invalid_request"))
    assertTrue(!body.has("statusCode"))
    assertTrue(!body.has("location"))
    assertTrue(!body.has("reason"))
    if (body["type"].asText() == "allowed") {
      assertTrue(!body.has("destination"))
    } else {
      assertTrue(body["destination"].asText().isNotBlank())
    }
    return body
  }

  private fun jsonResult(result: RouteResult): JsonNode = jacksonObjectMapper().valueToTree(result)

  private val defaultFailureHandler = DefaultRouteFailureHandler(
    unknownRouteDestination = RouteTarget("errors", "NotFound"),
    invalidRequestDestination = RouteTarget("errors", "Invalid")
  )

  private class TestConfig(
    override val id: String = "app",
    override val routes: List<RouteManifest>,
    handlers: List<RouteAccessHandler<*>> = emptyList(),
    override val htmlRenderer: HtmlRenderer = HtmlDocumentRenderer(
      bundleBasePath = "/assets",
      globalStylesheet = null
    ),
    evaluateApplication: (RouteRequest) -> AccessDecision = { AccessDecision.Allowed }
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
