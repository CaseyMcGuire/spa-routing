# Publishing

This project publishes two public artifacts:

- Core artifact: `io.github.caseymcguire:spa-routing-core`
- Gradle plugin: `io.github.caseymcguire.spa-routing`

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
./gradlew :spa-routing-gradle-plugin:publishPlugins
```

Publish `spa-routing-core` first because the Gradle plugin depends on it.
