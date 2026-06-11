plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
  id("com.vanniktech.maven.publish") version "0.36.0" apply false
  id("com.gradle.plugin-publish") version "2.1.1" apply false
}

extra["springBootVersion"] = "4.0.6"
extra["springFrameworkVersion"] = "7.0.7"

allprojects {
  group = "io.github.caseymcguire"
  version = "0.1.3"
}
