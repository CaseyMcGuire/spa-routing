import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-gradle-plugin`
  `maven-publish`
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
  plugins {
    create("spaRouting") {
      id = "com.caseymcguire.spa-routing"
      implementationClass = "com.caseymcguire.sparouting.gradle.SpaRoutingPlugin"
      displayName = "SPA Routing"
      description = "Adds configurable SPA route generation tasks."
    }
  }
}

tasks.test {
  useJUnitPlatform()
}
