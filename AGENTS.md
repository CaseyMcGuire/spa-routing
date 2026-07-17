# spa-routing

Kotlin library for type-safe SPA route definitions shared between a Spring Boot
server and a generated TypeScript client. Modules: `spa-routing-core`
(contracts), `spa-routing-gradle-plugin` (codegen), and
`spa-routing-spring-boot-autoconfigure` / `-starter` (runtime).

Build and test with `./gradlew build`.

## Breaking changes

Before assuming how the library behaves — especially anything your training
data or an older checkout suggests — read the breaking-changes entries in
[CHANGELOG.md](CHANGELOG.md). Behavior listed there has changed between
versions; the newest entry is the current semantics.

Most notably, rule evaluation is two-stage (application rules are a
deny-by-default gate; route-level rules are allow-by-default vetoes), so any
config, test, or example needs an application-level `Allow` — e.g. the
`AllowAll` builtin, or `RecordingRule(SpaRouteRuleResult.Allow)` in tests —
for a route to be served.

User-facing runtime docs live in
[docs/spring-boot-client-apps.md](docs/spring-boot-client-apps.md).
