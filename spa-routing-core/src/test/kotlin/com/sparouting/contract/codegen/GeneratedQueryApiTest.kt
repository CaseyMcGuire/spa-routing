package com.sparouting.contract.codegen

import com.sparouting.contract.SinglePageApplicationManifest
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedQueryApiTest {
  @Test
  fun `generated manifests compile and run without authored definitions`() = withGeneratedRoutes { output ->
    val sources = Files.list(output.resolve("server")).use { files ->
      files.filter { it.fileName.toString().endsWith("Manifest.kt") }.toList()
    }
    val classes = output.resolve("manifest-classes")
    val contractClasspath = listOf(SinglePageApplicationManifest::class.java, Unit::class.java)
      .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
      .joinToString(java.io.File.pathSeparator)
    val (result, diagnostics) = compileKotlin(sources, classes, baseClasspath = contractClasspath)
    assertEquals(ExitCode.OK, result, diagnostics)

    val parent = object : ClassLoader(javaClass.classLoader) {
      override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name.startsWith("com.sparouting.contract.codegen.") ||
          name == "com.sparouting.contract.SinglePageApplicationDefinition" ||
          name == "com.sparouting.contract.RouteDefinition") {
          throw ClassNotFoundException(name)
        }
        return super.loadClass(name, resolve)
      }
    }
    URLClassLoader(arrayOf(classes.toUri().toURL()), parent).use { loader ->
      fun manifest(name: String): SinglePageApplicationManifest =
        loader.loadClass("generated.$name").getConstructor().newInstance() as SinglePageApplicationManifest

      val root = manifest("ManifestTestManifest")
      assertEquals("manifesttest", root.id)
      assertEquals("Manifest Test", root.name)
      assertEquals("custom-assets", root.bundleName)
      assertEquals("/", root.routes.single { it.id == "Index" }.path)
      val post = root.routes.single { it.id == "Post" }
      assertEquals("/posts/{postId}", post.path)
      assertTrue(post.hasAccessHandler)
      assertFalse(post.hasValidParameterValues(emptyMap()))
      assertTrue(post.hasValidParameterValues(mapOf("postId" to "42")))
      assertFalse(post.hasValidQueryStringValues(emptyMap()))
      assertEquals("tag=a+b&tag=%E9%9B%AA", post.resolveQueryString(mapOf("tag" to listOf("a b", "雪"))))
      assertEquals("/posts/42", post.resolvePath(mapOf("postId" to "42")))

      val access = manifest("AccessTestManifest")
      assertEquals("/access/posts/{postId}", access.routes.single { it.id == "Post" }.path)
      assertTrue(access.routes.single { it.id == "Optional" }.parameters.single().optional)
      assertFalse(access.routes.single { it.id == "Public" }.hasAccessHandler)
      assertTrue(access.routes.single { it.id == "Post" }.queryString.single { it.name == "filter" }.repeated)
      val special = manifest("QueryTestManifest").routes.single { it.id == "Special" }
      assertTrue(special.queryString.any { it.name == "a\"$\n" && it.optional })
      assertEquals("/collision", manifest("RouteManifest").routes.single().path)
      assertTrue(manifest("SinglePageApplicationManifest").routes.isEmpty())
    }
  }

  @Test
  fun `generated access handlers compile decode typed requests and reject wrong request types`() = withGeneratedRoutes { output ->
    assertFalse(Files.exists(output.resolve("server/accesstest/PublicAccessHandler.kt")))
    assertFalse(Files.exists(output.resolve("server/accesstest/PublicRequest.kt")))
    val sources = Files.walk(output.resolve("server")).use { files ->
      files.filter { it.toString().endsWith(".kt") }.toList()
    }
    val classes = output.resolve("access-classes")
    val (result, diagnostics) = compileKotlin(
      sources + listOf(Path.of("src/test/fixtures/kotlin/AccessUsage.kt")), classes
    )
    assertEquals(ExitCode.OK, result, diagnostics)
    URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
      assertEquals("access verified", loader.loadClass("AccessUsageKt").getMethod("verifyAccess").invoke(null))
    }

    val invalid = output.resolve("WrongAccess.kt")
    invalid.writeText("""
      import com.sparouting.contract.AccessDecision
      import generated.accesstest.*
      class WrongAccess : PostAccessHandler() {
        override fun evaluate(request: StartRequest): AccessDecision = AccessDecision.Allow
      }
    """.trimIndent())
    val (failureResult, errors) = compileKotlin(listOf(invalid), output.resolve("invalid-access"), classes)
    assertEquals(ExitCode.COMPILATION_ERROR, failureResult, errors)
    assertContains(errors, "WrongAccess")
  }

  @Test
  fun `generated Kotlin builders and enums compile and preserve query values`() = withGeneratedRoutes { output ->
    val sources = Files.walk(output.resolve("server")).use { files ->
      files.filter { it.toString().endsWith(".kt") }.toList()
    }
    val classes = output.resolve("classes")
    val (result, diagnostics) = compileKotlin(
      sources + listOf(Path.of("src/test/fixtures/kotlin/QueryUsage.kt")), classes
    )
    assertEquals(ExitCode.OK, result, diagnostics)
    URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
      val url = loader.loadClass("QueryUsageKt").getMethod("verifyQueries").invoke(null)
      assertEquals(EXPECTED_URL, url)
    }

    val failures = mapOf(
      "MissingQuery" to "UserDetail(id = \"123\")",
      "MissingRequiredKey" to "UserDetail.QueryString()",
      "UnknownKey" to "UserDetail.QueryString(foo = \"x\", unknown = \"x\")",
      "WrongScalar" to "UserDetail.QueryString(foo = listOf(\"x\"))",
      "WrongList" to "Filters.QueryString(tag = \"x\")",
      "MissingRequiredList" to "Filters.QueryString()",
      "NoArgumentBypass" to "UserDetail()",
      "QueryOnlyBypass" to "Search()",
      "WrongEnum" to "UserDetail.queryString(emptyMap())[Search.QueryStringKey.Q]",
      "UnknownEnum" to "UserDetail.QueryStringKey.UNKNOWN"
    ).map { (name, expression) ->
      output.resolve("$name.kt").also {
        it.writeText("import generated.querytest.*\nfun $name() { $expression }\n")
      }
    }
    val (failureResult, errors) = compileKotlin(failures, output.resolve("invalid-classes"), classes)
    assertEquals(ExitCode.COMPILATION_ERROR, failureResult, errors)
    failures.forEach { assertContains(errors, it.fileName.toString(), message = errors) }
  }

  @Test
  fun `generated TypeScript builders and enums type check and run`() = withGeneratedRoutes { output ->
    verifyTypeScript(output, "query-api", EXPECTED_URL)
  }

  @Test
  fun `generated route objects infer renderer arguments and parse incoming values`() = withGeneratedRoutes { output ->
    verifyTypeScript(output, "route-object-api", "route objects verified")
  }

  private fun verifyTypeScript(output: Path, fixture: String, expectedOutput: String) {
    val client = output.resolve("client")
    Files.copy(Path.of("src/test/typescript/$fixture.ts"), client.resolve("$fixture.ts"))
    val compiled = output.resolve("javascript")
    runProcess(listOf(
      "node", System.getProperty("test.typescript.compiler"),
      "--strict", "--noEmitOnError", "--target", "ES2020", "--module", "commonjs",
      "--outDir", compiled.toString(), client.resolve("$fixture.ts").toString()
    ))
    assertEquals(expectedOutput, runProcess(listOf("node", compiled.resolve("$fixture.js").toString())).trim())
  }

  private fun compileKotlin(
    sources: List<Path>,
    destination: Path,
    additionalClasspath: Path? = null,
    baseClasspath: String = System.getProperty("test.runtime.classpath")
  ): Pair<ExitCode, String> {
    val diagnostics = ByteArrayOutputStream()
    val classpath = listOfNotNull(baseClasspath, additionalClasspath?.toString())
      .joinToString(java.io.File.pathSeparator)
    val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "21", "-classpath", classpath, "-d", destination.toString()) +
      sources.map { it.toString() }
    val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
    return result to diagnostics.toString(Charsets.UTF_8)
  }

  private fun runProcess(command: List<String>): String {
    val log = Files.createTempFile("spa-codegen-command", ".log")
    val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      error("Timed out running ${command.first()}")
    }
    val output = log.readText()
    assertEquals(0, process.exitValue(), output)
    Files.delete(log)
    return output
  }

  private fun withGeneratedRoutes(block: (Path) -> Unit) {
    val output = Files.createTempDirectory("spa-query-api")
    val properties = listOf("spa.application.source.dir", "route.output.dir", "route.server.package")
      .associateWith { System.getProperty(it) }
    try {
      System.setProperty("spa.application.source.dir", "src/test/kotlin")
      System.setProperty("route.server.package", "generated")
      System.setProperty("route.output.dir", output.resolve("server").toString())
      generateServerRoutes()
      System.setProperty("route.output.dir", output.resolve("client").toString())
      generateClientRoutes()
      block(output)
    } finally {
      properties.forEach { (name, value) ->
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
      }
      output.toFile().deleteRecursively()
    }
  }

  companion object {
    private const val EXPECTED_URL = "/querytest/users/123?foo=a+b%2B%26%3D%E9%9B%AA&baz=&tag=x%2Fy&tag=%C3%A9"
  }
}
