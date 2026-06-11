package io.github.caseymcguire.sparouting.spring.config

import com.caseymcguiredotcom.sparoutecontract.int
import com.caseymcguiredotcom.sparoutecontract.route
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult
import io.github.caseymcguire.sparouting.spring.testsupport.RecordingRule
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaRouteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SinglePageApplicationRouteRegistryTest {
  @Test
  fun `indexes routes by application and route id`() {
    val config = TestSinglePageApplicationConfig(
      TestSpaApplicationDefinition(
        routes = listOf(route("users/{id}", "UserDetail", listOf(int("id"))))
      )
    )

    val registry = SinglePageApplicationRouteRegistry(listOf(config))

    val registration = registry.findByApplicationAndRouteId("test", "UserDetail")
    assertNotNull(registration)
    assertEquals(config, registration.application)
    assertEquals("UserDetail", registration.route.id)
    assertEquals(1, registry.registrations().size)
  }

  @Test
  fun `rejects duplicate application ids`() {
    val first = TestSinglePageApplicationConfig(
      TestSpaApplicationDefinition(id = "duplicate", routes = listOf(route("first", "First")))
    )
    val second = TestSinglePageApplicationConfig(
      TestSpaApplicationDefinition(id = "duplicate", routes = listOf(route("second", "Second")))
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(first, second))
    }
  }

  @Test
  fun `rejects duplicate route ids within an application`() {
    val config = TestSinglePageApplicationConfig(
      TestSpaApplicationDefinition(
        routes = listOf(
          route("first", "Duplicate"),
          route("second", "Duplicate")
        )
      )
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config))
    }
  }

  @Test
  fun `rejects route rules for unknown routes`() {
    val config = TestSinglePageApplicationConfig(
      application = TestSpaApplicationDefinition(routes = listOf(route("known", "Known"))),
      routeRules = mapOf(
        TestSpaRouteKey("test", "Missing") to listOf(RecordingRule(SpaRouteRuleResult.Allow))
      )
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(config))
    }
  }
}
