import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-gradle-plugin`
  id("com.gradle.plugin-publish")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

dependencies {
  implementation(project(":spa-routing-core"))
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
  testImplementation(kotlin("test"))
  testImplementation(gradleTestKit())
}

gradlePlugin {
  website.set("https://github.com/caseymcguire/spa-routing")
  vcsUrl.set("https://github.com/caseymcguire/spa-routing.git")

  plugins {
    create("spaRouting") {
      id = "io.github.caseymcguire.spa-routing"
      implementationClass = "io.github.caseymcguire.sparouting.gradle.SpaRoutingPlugin"
      displayName = "SPA Routing"
      description = "Adds configurable SPA route generation tasks."
      tags.set(listOf("kotlin", "spa", "routing", "codegen"))
    }
  }
}

tasks.test {
  useJUnitPlatform()
}
