import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("com.vanniktech.maven.publish")
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
  api(project(":spa-routing-core"))
  testImplementation(kotlin("test"))
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
    groupId = "com.sparouting",
    artifactId = "spa-routing-runtime",
    version = project.version.toString()
  )

  pom {
    name.set("spa-routing-runtime")
    description.set("Framework-neutral runtime for SPA routing.")
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
