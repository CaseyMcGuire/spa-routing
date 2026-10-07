import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("com.vanniktech.maven.publish")
}

val ktorVersion: String by rootProject.extra

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
  api(project(":spa-routing-runtime"))
  api("io.ktor:ktor-server-core:$ktorVersion")

  testImplementation(kotlin("test"))
  testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
  testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
  testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
}

tasks.test {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()

  if (hasSigningCredentials()) {
    signAllPublications()
  }

  coordinates(
    groupId = "io.github.caseymcguire",
    artifactId = "spa-routing-ktor",
    version = project.version.toString()
  )

  pom {
    name.set("spa-routing-ktor")
    description.set("Ktor HTTP integration for SPA routing.")
    url.set("https://github.com/CaseyMcGuire/spa-routing")

    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }

    developers {
      developer {
        id.set("caseymcguire")
        name.set("Casey McGuire")
        url.set("https://github.com/CaseyMcGuire")
      }
    }

    scm {
      url.set("https://github.com/CaseyMcGuire/spa-routing")
      connection.set("scm:git:git://github.com/CaseyMcGuire/spa-routing.git")
      developerConnection.set("scm:git:ssh://git@github.com:CaseyMcGuire/spa-routing.git")
    }
  }
}

fun hasSigningCredentials(): Boolean {
  return providers.gradleProperty("signingInMemoryKey").isPresent ||
    providers.gradleProperty("signing.secretKeyRingFile").isPresent
}
