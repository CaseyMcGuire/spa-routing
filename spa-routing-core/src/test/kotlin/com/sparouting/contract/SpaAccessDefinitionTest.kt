package com.sparouting.contract

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaAccessDefinitionTest {
  @Test
  fun `access handler generation is explicit`() {
    assertFalse(route("home", "Home").generateAccessHandler)
    assertTrue(route("home", "Home", generateAccessHandler = true).generateAccessHandler)
  }

  @Test
  fun `generated access types cannot collide with route names`() {
    for (name in listOf("PostAccessHandler", "PostRequest")) {
      val application = object : SpaApplicationDefinition {
        override val id = "test"
        override val name = "Test"
        override val urlPrefix = "test"
        override val appRootPath = "src/test"
        override val routes = listOf(
          route("posts/{id}", "Post", generateAccessHandler = true),
          route("other", name)
        )
      }
      val error = assertFailsWith<IllegalArgumentException> {
        SpaApplicationDefinitionValidator.validate(listOf(application))
      }
      assertContains(error.message.orEmpty(), name)
    }
  }
}
