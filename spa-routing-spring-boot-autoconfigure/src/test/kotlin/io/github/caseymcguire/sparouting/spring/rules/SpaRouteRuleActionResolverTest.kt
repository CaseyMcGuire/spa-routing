package io.github.caseymcguire.sparouting.spring.rules

import com.caseymcguiredotcom.sparoutecontract.SpaRouteTarget
import com.caseymcguiredotcom.sparoutecontract.int
import com.caseymcguiredotcom.sparoutecontract.route
import io.github.caseymcguire.sparouting.spring.testsupport.TestSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.testsupport.TestSpaApplicationDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpaRouteRuleActionResolverTest {
  private val config = TestSinglePageApplicationConfig(
    TestSpaApplicationDefinition(
      routes = listOf(route("users/{id}", "UserDetail", listOf(int("id"))))
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
    val response = resolver.resolve(
      SpaRouteRuleAction.redirectTo(
        SpaRouteTarget("test", "UserDetail", mapOf("id" to "42"))
      )
    )

    assertEquals(302, response.statusCode)
    assertEquals("/test/users/42", response.location)
  }

  @Test
  fun `unknown target app throws`() {
    assertFailsWith<IllegalStateException> {
      resolver.resolve(
        SpaRouteRuleAction.redirectTo(SpaRouteTarget("missing", "UserDetail", mapOf("id" to "42")))
      )
    }
  }

  @Test
  fun `unknown target route throws`() {
    assertFailsWith<IllegalStateException> {
      resolver.resolve(
        SpaRouteRuleAction.redirectTo(SpaRouteTarget("test", "Missing", mapOf("id" to "42")))
      )
    }
  }

  @Test
  fun `invalid target params throws`() {
    assertFailsWith<IllegalArgumentException> {
      resolver.resolve(
        SpaRouteRuleAction.redirectTo(SpaRouteTarget("test", "UserDetail", mapOf("id" to "not-an-int")))
      )
    }
  }
}
