plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

val springBootVersion: String by rootProject.extra

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

dependencies {
  api(project(":spa-routing-core"))
  api(project(":spa-routing-spring-boot-autoconfigure"))
  api("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
}

mavenPublishing {
  publishToMavenCentral()

  if (hasSigningCredentials()) {
    signAllPublications()
  }

  coordinates(
    groupId = "io.github.caseymcguire",
    artifactId = "spa-routing-spring-boot-starter",
    version = project.version.toString()
  )

  pom {
    name.set("spa-routing-spring-boot-starter")
    description.set("Spring Boot starter for SPA routing.")
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
