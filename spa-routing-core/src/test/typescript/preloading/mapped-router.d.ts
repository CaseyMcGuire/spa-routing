// Framework-free model of the library's mapped router signature. This is a
// compiler fixture, not generated adapter output or a router implementation.
import type { RouteDefinition, SpaRouteContext } from "./adapter-contract";

export declare function createSpaRouter<
  Routes extends Record<string, RouteDefinition>,
  Results extends Record<keyof Routes, unknown> = Record<keyof Routes, never>,
>(routes: Routes, config: {
  [Key in keyof Results]-?: Key extends keyof NoInfer<Routes> ? {
    preload?: (context: SpaRouteContext<Routes[Key]>) => Results[Key];
    render: (context: NoInfer<SpaRouteContext<Routes[Key]> & (
      unknown extends Results[Key] ? {} : [Results[Key]] extends [never] ? {} : { preload: Results[Key] }
    )>) => unknown;
  } : never;
}): unknown;
