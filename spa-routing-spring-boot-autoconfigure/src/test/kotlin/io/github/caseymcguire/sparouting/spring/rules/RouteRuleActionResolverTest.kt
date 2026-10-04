package io.github.caseymcguire.sparouting.spring.rules

import com.sparouting.contract.RouteTarget
import com.sparouting.contract.route
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteRuleActionResolverTest {
  private val config = TestSinglePageApplicationConfig(
    TestSinglePageApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail"))
    )
  )

  private val resolver = RouteRuleActionResolver(listOf(config))

  @Test
  fun `raw redirect resolves to status and location`() {
    val response = resolver.resolve(RouteRuleAction.redirect("/login"))

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
  }

  @Test
  fun `typed route redirect resolves to full url`() {
    for (id in listOf("user-42", "0042", "550e8400-e29b-41d4-a716-446655440000")) {
      val response = resolver.resolve(
        RouteRuleAction.redirectTo(
          RouteTarget("test", "UserDetail", mapOf("id" to id))
        )
      )

      assertEquals(302, response.statusCode)
      assertEquals("/test/users/$id", response.location)
    }
  }

  @Test
  fun `unknown target app throws`() {
    assertFailsWith<IllegalStateException> {
      resolver.resolve(
        RouteRuleAction.redirectTo(RouteTarget("missing", "UserDetail", mapOf("id" to "550e8400-e29b-41d4-a716-446655440000")))
      )
    }
  }

  @Test
  fun `unknown target route throws`() {
    assertFailsWith<IllegalStateException> {
      resolver.resolve(
        RouteRuleAction.redirectTo(RouteTarget("test", "Missing", mapOf("id" to "550e8400-e29b-41d4-a716-446655440000")))
      )
    }
  }

  @Test
  fun `invalid target params throws`() {
    for (parameters in listOf(emptyMap(), mapOf("id" to "user-42", "unknown" to "value"))) {
      assertFailsWith<IllegalArgumentException> {
        resolver.resolve(
          RouteRuleAction.redirectTo(RouteTarget("test", "UserDetail", parameters))
        )
      }
    }
  }
}
