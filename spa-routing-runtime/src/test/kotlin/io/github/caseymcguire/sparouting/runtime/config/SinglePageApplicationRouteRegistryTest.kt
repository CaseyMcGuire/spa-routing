package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.Route
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.testsupport.TestSinglePageApplicationDefinition
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
      TestSinglePageApplicationDefinition(
        routes = listOf(route("users/{id}", "UserDetail"))
      )
    )

    val handler = applicationAccessHandler(config.application)
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
      TestSinglePageApplicationDefinition(id = "duplicate", routes = listOf(route("first", "First")))
    )
    val second = TestSinglePageApplicationConfig(
      TestSinglePageApplicationDefinition(id = "duplicate", routes = listOf(route("second", "Second")))
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(first, second), emptyList())
    }
  }

  @Test
  fun `rejects duplicate route ids within an application`() {
    val config = TestSinglePageApplicationConfig(
      TestSinglePageApplicationDefinition(
        routes = listOf(
          route("first", "Duplicate"),
          route("second", "Duplicate")
        )
      )
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config), emptyList())
    }
  }

  @Test
  fun `missing application handlers are rejected even for apps without routes`() {
    for (routes in listOf(emptyList(), listOf(route("", "Index")))) {
      val config = TestSinglePageApplicationConfig(TestSinglePageApplicationDefinition(routes = routes))
      val failure = assertFailsWith<IllegalArgumentException> {
        SinglePageApplicationRouteRegistry(listOf(config), emptyList())
      }
      assertContains(failure.message.orEmpty(), "Missing application access handler for test")
    }
  }

  @Test
  fun `duplicate application handlers are rejected`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationDefinition(routes = listOf(route("", "Index"))))
    val handlers = listOf(applicationAccessHandler(config.application), applicationAccessHandler(config.application))
    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config), handlers)
    }
    assertContains(failure.message.orEmpty(), "Expected exactly one application access handler for test, found 2")
  }

  @Test
  fun `handlers for unknown applications are rejected`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationDefinition(routes = listOf(route("", "Index"))))
    val unknown = TestSinglePageApplicationDefinition(id = "unknown", routes = emptyList())
    val failure = assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(
        listOf(config), listOf(applicationAccessHandler(config.application), applicationAccessHandler(unknown))
      )
    }
    assertContains(failure.message.orEmpty(), "Application access handler registered for unknown application: unknown")
  }

  @Test
  fun `registration binds the application and route handler instances`() {
    val config = TestSinglePageApplicationConfig(TestSinglePageApplicationDefinition(
      routes = listOf(route("posts/{id}", "Post", generateAccessHandler = true))
    ))
    val applicationHandler = applicationAccessHandler(config.application)
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
