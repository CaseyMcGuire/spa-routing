package com.sparouting.runtime.config

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteManifest
import com.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import com.sparouting.runtime.testsupport.routeAccessHandlers
import com.sparouting.runtime.testsupport.applicationAccessHandler
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
      routes = listOf(RouteManifest("/test/users/{id}", "UserDetail"))
    )

    val handler = config.applicationAccessHandler
    val registry = SinglePageApplicationRouteRegistry(listOf(config))

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
    val first = TestSinglePageApplicationConfig(id = "duplicate", routes = listOf(RouteManifest("/test/first", "First")))
    val second = TestSinglePageApplicationConfig(id = "duplicate", routes = listOf(RouteManifest("/test/second", "Second")))

    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(first, second))
    }
    assertContains(failure.message.orEmpty(), "Duplicate single page application IDs")
  }

  @Test
  fun `rejects duplicate route ids within an application`() {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(
        RouteManifest("/test/first", "Duplicate"),
        RouteManifest("/test/second", "Duplicate")
      )
    )

    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config))
    }
    assertContains(failure.message.orEmpty(), "duplicate route IDs")
  }

  @Test
  fun `duplicate paths and relative paths are rejected in runtime configs`() {
    for ((routes, message) in listOf(
      listOf(RouteManifest("/test/path", "First"), RouteManifest("/test/path", "Second")) to "duplicate route URLs",
      listOf(RouteManifest("relative", "Relative")) to "must have an absolute path pattern"
    )) {
      val config = TestSinglePageApplicationConfig(routes = routes)
      val failure = assertFailsWith<IllegalArgumentException> {
        SinglePageApplicationRouteRegistry(listOf(config))
      }
      assertContains(failure.message.orEmpty(), message)
    }
  }

  @Test
  fun `custom collections reject missing duplicate unknown unflagged and foreign handlers`() {
    val config = TestSinglePageApplicationConfig(routes = listOf(
      RouteManifest("/test/posts/{id}", "Post", hasAccessHandler = true),
      RouteManifest("/test/public", "Public")
    ))
    val post = handler("test", "Post")
    val invalidCollections = listOf(
      routeAccessHandlers() to "Missing access handler for test:Post",
      routeAccessHandlers(post, post) to "Expected exactly one access handler for test:Post, found 2",
      routeAccessHandlers(post, handler("test", "Unknown")) to "Access handler registered for test:Unknown",
      routeAccessHandlers(post, handler("test", "Public")) to "does not declare hasAccessHandler = true",
      routeAccessHandlers(post, handler("other", "Post")) to "belongs to a different application than test"
    )
    for ((handlers, message) in invalidCollections) {
      val failure = assertFailsWith<IllegalArgumentException> {
        SinglePageApplicationRouteRegistry(listOf(config.copy(routeAccessHandlers = handlers)))
      }
      assertContains(failure.message.orEmpty(), message)
    }
  }

  @Test
  fun `application with no gated routes accepts an empty handler collection`() {
    val config = TestSinglePageApplicationConfig(routes = emptyList())
    assertEquals(emptyList(), SinglePageApplicationRouteRegistry(listOf(config)).registrations())
  }

  @Test
  fun `registration binds the application and route handler instances`() {
    val config = TestSinglePageApplicationConfig(
      routes = listOf(RouteManifest("/test/posts/{id}", "Post", hasAccessHandler = true))
    )
    val applicationHandler = applicationAccessHandler()
    val routeHandler = object : RouteAccessHandler<RouteAccessContext>(Route("test", "Post")) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context
      override fun evaluate(request: RouteAccessContext): AccessDecision = AccessDecision.Allow
    }
    val registry = SinglePageApplicationRouteRegistry(listOf(config.copy(
      applicationAccessHandler = applicationHandler, routeAccessHandlers = routeAccessHandlers(routeHandler)
    )))

    val registration = registry.findByApplicationAndRouteId("test", "Post")
    assertNotNull(registration)
    assertSame(applicationHandler, registration.applicationAccessHandler)
    assertSame(routeHandler, registration.routeAccessHandler)
  }

  private fun handler(applicationId: String, routeId: String): RouteAccessHandler<RouteAccessContext> {
    return object : RouteAccessHandler<RouteAccessContext>(Route(applicationId, routeId)) {
      override fun createRequest(context: RouteAccessContext): RouteAccessContext = context
      override fun evaluate(request: RouteAccessContext): AccessDecision = AccessDecision.Allow
    }
  }
}
