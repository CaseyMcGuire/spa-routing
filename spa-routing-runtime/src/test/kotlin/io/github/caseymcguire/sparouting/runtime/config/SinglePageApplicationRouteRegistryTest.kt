package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationManifest
import io.github.caseymcguire.sparouting.runtime.testsupport.applicationAccessHandler
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SinglePageApplicationRouteRegistryTest {
  @Test
  fun `indexes routes by application and route id`() {
    val config = TestSinglePageApplicationConfig(
      TestSinglePageApplicationManifest(
        routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))
      )
    )

    val handler = applicationAccessHandler(config.manifest)
    val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(handler))

    val registration = registry.findByApplicationAndRouteId("test", "UserDetail")
    assertNotNull(registration)
    assertEquals(config, registration.application)
    assertEquals("UserDetail", registration.route.id)
    assertSame(handler, registration.applicationAccessHandler)
    assertNull(registration.routeAccessHandler)
    assertEquals(1, registry.registrations().size)
  }

  @Test
  fun `rejects duplicate application ids`() {
    val first = TestSinglePageApplicationConfig(
      TestSinglePageApplicationManifest(id = "duplicate", routes = listOf(RouteManifest("/test/first", "First")))
    )
    val second = TestSinglePageApplicationConfig(
      TestSinglePageApplicationManifest(id = "duplicate", routes = listOf(RouteManifest("/test/second", "Second")))
    )

    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(first, second), emptyList())
    }
    assertContains(failure.message.orEmpty(), "Duplicate single page application IDs")
  }

  @Test
  fun `rejects duplicate route ids within an application`() {
    val config = TestSinglePageApplicationConfig(
      TestSinglePageApplicationManifest(
        routes = listOf(
          RouteManifest("/test/first", "Duplicate"),
          RouteManifest("/test/second", "Duplicate")
        )
      )
    )

    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config), emptyList())
    }
    assertContains(failure.message.orEmpty(), "duplicate route IDs")
  }

  @Test
  fun `duplicate paths and relative paths are rejected in runtime manifests`() {
    for ((routes, message) in listOf(
      listOf(RouteManifest("/test/path", "First"), RouteManifest("/test/path", "Second")) to "duplicate route URLs",
      listOf(RouteManifest("relative", "Relative")) to "must have an absolute path pattern"
    )) {
      val config = TestSinglePageApplicationConfig(TestSinglePageApplicationManifest(routes = routes))
      val failure = assertFailsWith<IllegalArgumentException> {
        SinglePageApplicationRouteRegistry(listOf(config), listOf(applicationAccessHandler(config.manifest)))
      }
      assertContains(failure.message.orEmpty(), message)
    }
  }

  @Test
  fun `missing application handlers are rejected even for apps without routes`() {
    for (routes in listOf(emptyList(), listOf(RouteManifest("/test", "Index")))) {
      val config = TestSinglePageApplicationConfig(TestSinglePageApplicationManifest(routes = routes))
      val failure = assertFailsWith<IllegalArgumentException> {
        SinglePageApplicationRouteRegistry(listOf(config), emptyList())
      }
      assertContains(failure.message.orEmpty(), "Missing application access handler for test")
    }
  }

  @Test
  fun `duplicate application handlers are rejected`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationManifest(routes = listOf(RouteManifest("/test", "Index"))))
    val handlers = listOf(applicationAccessHandler(config.manifest), applicationAccessHandler(config.manifest))
    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config), handlers)
    }
    assertContains(failure.message.orEmpty(), "Expected exactly one application access handler for test, found 2")
  }

  @Test
  fun `handlers for unknown applications are rejected`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationManifest(routes = listOf(RouteManifest("/test", "Index"))))
    val unknown = TestSinglePageApplicationManifest(id = "unknown", routes = emptyList())
    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(
        listOf(config), listOf(applicationAccessHandler(config.manifest), applicationAccessHandler(unknown))
      )
    }
    assertContains(failure.message.orEmpty(), "Application access handler registered for unknown application: unknown")
  }

  @Test
  fun `registration binds the application and route handler instances`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationManifest(
      routes = listOf(RouteManifest("/test/posts/{id}", "Post", hasAccessHandler = true))
    ))
    val applicationHandler = applicationAccessHandler(config.manifest)
    val routeHandler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Post")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context
      override fun evaluate(request: RouteAccessContext): AccessDecision = AccessDecision.Allow
    }
    val registry = SinglePageApplicationRouteRegistry(listOf(config), listOf(applicationHandler), listOf(routeHandler))

    val registration = registry.findByApplicationAndRouteId("test", "Post")
    assertNotNull(registration)
    assertSame(applicationHandler, registration.applicationAccessHandler)
    assertSame(routeHandler, registration.routeAccessHandler)
  }

}
