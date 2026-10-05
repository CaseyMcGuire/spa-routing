# Publishing

This project publishes these public artifacts:

- Core artifact: `com.sparouting:spa-routing-core`
- Shared runtime: `com.sparouting:spa-routing-runtime`
- Ktor adapter: `com.sparouting:spa-routing-ktor`
- Gradle plugin: `com.sparouting.spa-routing`
- Spring Boot auto-configuration: `com.sparouting:spa-routing-spring-boot-autoconfigure`
- Spring Boot starter: `com.sparouting:spa-routing-spring-boot-starter`

## Credentials

Release publishing requires:

- Maven Central credentials
- Gradle Plugin Portal credentials
- GPG signing credentials

Keep secrets in `~/.gradle/gradle.properties` or CI secrets, not in this repository.

```properties
mavenCentralUsername=...
mavenCentralPassword=...
signingInMemoryKey=...
signingInMemoryKeyPassword=...
gradle.publish.key=...
gradle.publish.secret=...
```

Local publishing does not require signing credentials. Maven Central releases do.

## Validate

```sh
./gradlew clean build
./gradlew publishToMavenLocal
```

Validate the Gradle Plugin Portal publication without uploading. This still requires Plugin Portal credentials:

```sh
./gradlew :spa-routing-gradle-plugin:publishPlugins --validate-only
```

## Release

Bump the project version in `build.gradle.kts`, update README examples to match, then publish:

```sh
./gradlew :spa-routing-core:publishAndReleaseToMavenCentral
./gradlew :spa-routing-runtime:publishAndReleaseToMavenCentral
./gradlew :spa-routing-ktor:publishAndReleaseToMavenCentral
./gradlew :spa-routing-spring-boot-autoconfigure:publishAndReleaseToMavenCentral
./gradlew :spa-routing-spring-boot-starter:publishAndReleaseToMavenCentral
./gradlew :spa-routing-gradle-plugin:publishPlugins
```

Publish `spa-routing-core` first because the Gradle plugin and runtime depend on it.
Publish `spa-routing-runtime` before `spa-routing-ktor` and `spa-routing-spring-boot-autoconfigure`, then
publish `spa-routing-spring-boot-starter`. Use the same version for all artifacts.
