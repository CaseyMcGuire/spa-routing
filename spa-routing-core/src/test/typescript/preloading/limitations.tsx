import { PreloadPrototypeRoutes as WikiRoutes } from "../PreloadPrototypeRoutes";
import { createSpaRouter } from "./WikiRouter";
import type { SpaRouteContext } from "./adapter-contract";
import { environment, expectTrue, loadQuery, ViewWikiPage, ViewWikiPageQuery } from "./resources";
import type { Equal } from "./resources";

const otherRoutes = {
  Edit: { render: () => null },
  About: { render: () => null },
  Section: { render: () => null },
};

// Regression control for microsoft/TypeScript#53018 on TypeScript 5.9.3.
declare function mappedRouter<Results extends Record<keyof typeof WikiRoutes, unknown>>(config: {
  [Key in keyof typeof WikiRoutes]: {
    preload?: (context: SpaRouteContext<(typeof WikiRoutes)[Key]>) => Results[Key];
    render: (context: SpaRouteContext<(typeof WikiRoutes)[Key]> & { preload: Results[Key] }) => unknown;
  };
}): void;

mappedRouter({
  ...otherRoutes,
  View: {
    preload: ({ params }) => loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId }),
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, unknown>>();
      // @ts-expect-error The reverse mapped signature loses the resource type.
      return <ViewWikiPage queryRef={preload} />;
    },
  },
});

// The concrete signature still follows left-to-right intra-expression inference.
// Put the producer before its consumer, as in the requested developer experience.
createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    // @ts-expect-error The preload result has not been inferred yet.
    render: ({ preload }) => null,
    // @ts-expect-error Result has already defaulted to the no-preload marker.
    preload: ({ params }) => params.wikiId,
  },
});
