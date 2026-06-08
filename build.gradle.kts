import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.21"
  `java-library`
  `maven-publish`
}

group = "com.caseymcguire"
version = "0.1.0-SNAPSHOT"

java {
  withSourcesJar()
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
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])

      pom {
        name.set("spa-routing")
        description.set("Reusable SPA routing contract and code generators.")
      }
    }
  }
}
