package io.github.caseymcguire.sparouting.spring.rules

import com.sparouting.contract.SpaRouteTarget
import com.sparouting.contract.route
import com.sparouting.contract.string
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpaRouteRuleActionResolverTest {
  private val config = TestSinglePageApplicationConfig(
    TestSpaApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail", listOf(string("id"))))
    )
  )

  private val resolver = SpaRouteRuleActionResolver(listOf(config))

  @Test
  fun `raw redirect resolves to status and location`() {
    val response = resolver.resolve(SpaRouteRuleAction.redirect("/login"))

    assertEquals(302, response.statusCode)
    assertEquals("/login", response.location)
  }

  @Test
  fun `typed route redirect resolves to full url`() {
    for (id in listOf("user-42", "0042", "550e8400-e29b-41d4-a716-446655440000")) {
      val response = resolver.resolve(
        SpaRouteRuleAction.redirectTo(
          SpaRouteTarget("test", "UserDetail", mapOf("id" to id))
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
        SpaRouteRuleAction.redirectTo(SpaRouteTarget("missing", "UserDetail", mapOf("id" to "550e8400-e29b-41d4-a716-446655440000")))
      )
    }
  }

  @Test
  fun `unknown target route throws`() {
    assertFailsWith<IllegalStateException> {
      resolver.resolve(
        SpaRouteRuleAction.redirectTo(SpaRouteTarget("test", "Missing", mapOf("id" to "550e8400-e29b-41d4-a716-446655440000")))
      )
    }
  }

  @Test
  fun `invalid target params throws`() {
    for (parameters in listOf(emptyMap(), mapOf("id" to "user-42", "unknown" to "value"))) {
      assertFailsWith<IllegalArgumentException> {
        resolver.resolve(
          SpaRouteRuleAction.redirectTo(SpaRouteTarget("test", "UserDetail", parameters))
        )
      }
    }
  }
}
