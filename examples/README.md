# Examples

A simple blog running on Spring Boot or Ktor, with shared business logic,
generated routes, access handlers, and a React UI using spa-kit.
Each server keeps its own in-memory data, which resets on restart.

## Layout

- [route-definitions](route-definitions/) — authored blog routes.
- [blog](blog/) — shared models, service, access handlers, and generated Kotlin routes.
- [frontend](frontend/) — shared React UI and generated TypeScript routes.
- [spring](spring/) — Spring Boot HTTP endpoints and wiring.
- [ktor](ktor/) — Ktor HTTP endpoints and wiring.

## Run

Use JDK 21 and Node 22.12 or newer, with npm on `PATH`.
Run either command from the repository root:

```sh
./gradlew :examples:spring:run
```

Spring: [http://localhost:8080/](http://localhost:8080/).

```sh
./gradlew :examples:ktor:run
```

Ktor: [http://localhost:8082/](http://localhost:8082/).

Gradle generates the routes and builds the frontend automatically.
Restart the server after frontend changes to rebuild the assets.

To choose another port:

```sh
./gradlew :examples:spring:run --args='--server.port=8083'
PORT=8084 ./gradlew :examples:ktor:run
```

## Build

```sh
./gradlew :examples:build
```

For library integration details, see [Spring Boot setup](../docs/spring-boot-client-apps.md),
[Ktor setup](../docs/ktor.md), and [runtime behavior](../docs/runtime.md).
