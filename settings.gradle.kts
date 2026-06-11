pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
  }
}

rootProject.name = "spa-routing"
include("spa-routing-core")
include("spa-routing-gradle-plugin")
include("spa-routing-spring-boot-autoconfigure")
include("spa-routing-spring-boot-starter")
