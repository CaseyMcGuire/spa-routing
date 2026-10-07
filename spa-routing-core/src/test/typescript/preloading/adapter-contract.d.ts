// Prototype of types that would belong to @spa-kit/react-router.
// No runtime is implemented here. Render output and Router are deliberately opaque:
// integration would use ReactNode and the library's actual router/options types.
export interface RouteDefinition {
  readonly path: string;
  readonly applicationId: string;
  readonly routeId: string;
  readonly hasAccessHandler: boolean;
  parse(
    params: Readonly<Record<string, string | undefined>>,
    search: URLSearchParams,
  ): { params: object; queryString: object } | null;
}

export type SpaRouteContext<Route extends RouteDefinition> = NonNullable<ReturnType<Route["parse"]>>;

declare const noPreload: unique symbol;
export type NoPreload = typeof noPreload;

// `never` is a real callback return type, not an absent callback.
type IsAbsent<Result> = [Result] extends [never] ? false : [Result] extends [NoPreload] ? true : false;

type Preload<Context, Result> = (context: Context) => Result;

export type RenderContext<Context, Result> =
  IsAbsent<Result> extends true ? Context : Context & { preload: Result };

export type RouteConfig<Route extends RouteDefinition, Result = NoPreload> = {
  preload?: Preload<SpaRouteContext<Route>, Result>;
  // Only the preload return value may infer Result; render cannot invent it.
  render: (context: RenderContext<SpaRouteContext<Route>, NoInfer<Result>>) => unknown;
} & (
  // Reject a possibly absent callback when render expects its resource.
  IsAbsent<NoInfer<Result>> extends true
    ? {}
    : { preload: Preload<SpaRouteContext<Route>, Result> }
);

export interface PrototypeRouter {
  readonly prototypeRouter: unique symbol;
}
