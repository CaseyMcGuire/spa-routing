package io.github.caseymcguire.sparouting.spring.config

import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
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
      TestSinglePageApplicationDefinition(id = "duplicate", routes = listOf(route("first", "First")))
    )
    val second = TestSinglePageApplicationConfig(
      TestSinglePageApplicationDefinition(id = "duplicate", routes = listOf(route("second", "Second")))
    )

    assertFailsWith<IllegalArgumentException> {
      SinglePageApplicationRouteRegistry(listOf(first, second))
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
      SinglePageApplicationRouteRegistry(listOf(config))
    }
  }
}
