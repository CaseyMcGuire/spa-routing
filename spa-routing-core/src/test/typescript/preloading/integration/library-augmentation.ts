// Separate probe against the installed @spa-kit/react-router 0.2.0 declarations.
// This demonstrates why the prototype recommends a local adapter export.
import { createSpaRouter } from "@spa-kit/react-router";
import type { NoPreload, PrototypeRouter, RouteConfig, SpaRouteContext } from "../adapter-contract";
import type { Equal, IsAny } from "../resources";

declare function exact<T extends true>(): void;

interface Route<Context> {
  readonly path: string;
  readonly applicationId: string;
  readonly routeId: string;
  readonly hasAccessHandler: boolean;
  parse(params: Readonly<Record<string, string | undefined>>, query: URLSearchParams): Context | null;
}

declare const WikiRoutes: {
  View: Route<{ params: { wikiId: string }; queryString: { revision?: string } }>;
  About: Route<{ params: {}; queryString: {} }>;
};

declare module "@spa-kit/react-router" {
  function createSpaRouter<View = NoPreload, About = NoPreload>(
    routes: typeof WikiRoutes,
    config: {
      View: RouteConfig<typeof WikiRoutes.View, View>;
      About: RouteConfig<typeof WikiRoutes.About, About>;
    },
  ): PrototypeRouter;
}

createSpaRouter(WikiRoutes, {
  View: {
    preload: ({ params }) => ({ data: params.wikiId }),
    render: ({ preload }) => {
      exact<Equal<typeof preload, { data: string }>>();
      // @ts-expect-error Cannot use a different resource.
      const wrongResource: { other: string } = preload;
      return null;
    },
  },
  About: {
    render: (context) => {
      exact<Equal<typeof context, SpaRouteContext<typeof WikiRoutes.About>>>();
      // @ts-expect-error No preload.
      context.preload;
      return null;
    },
  },
});

// The augmented overload changes contextual typing of an existing library call.
createSpaRouter(WikiRoutes, {
  // @ts-expect-error The augmentation changes this formerly valid contextual parameter.
  View: { render: ({ wikiId }) => wikiId },
  About: { render: () => null },
});

// A second application has the same keys but a narrower parser result.
// Overload resolution loses contextual typing of its callback altogether.
declare const OtherRoutes: {
  View: Route<{ params: { wikiId: string }; queryString: {} }>;
  About: Route<{ params: {}; queryString: {} }>;
};

createSpaRouter(OtherRoutes, {
  View: {
    // @ts-expect-error This binding becomes implicit any under the global overload.
    preload: ({ queryString }) => {
      exact<Equal<IsAny<typeof queryString>, true>>();
      return queryString.revision;
    },
    render: () => null,
  },
  About: { render: () => null },
});
