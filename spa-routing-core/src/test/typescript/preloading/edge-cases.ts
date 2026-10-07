import { PreloadPrototypeRoutes as WikiRoutes } from "../PreloadPrototypeRoutes";
import { createSpaRouter } from "./WikiRouter";
import type { SpaRouteContext } from "./adapter-contract";
import { environment, expectTrue, loadQuery, ViewWikiPageQuery } from "./resources";
import type { Equal, PreloadedQuery, ViewQuery } from "./resources";

const otherRoutes = {
  Edit: { render: () => null },
  About: { render: () => null },
  Section: { render: () => null },
};

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    render: (context) => {
      expectTrue<Equal<typeof context, SpaRouteContext<typeof WikiRoutes.View>>>();
      // @ts-expect-error A route with access gating need not have a preload.
      context.preload;
      return null;
    },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => { throw new Error(params.wikiId); },
    render: (context) => {
      expectTrue<Equal<typeof context.preload, never>>();
      expectTrue<Equal<Extract<keyof typeof context, string>, "params" | "queryString" | "preload">>();
      return null;
    },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => { void params.wikiId; },
    render: ({ preload }) => { expectTrue<Equal<typeof preload, void>>(); return null; },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => { void params.wikiId; return undefined; },
    render: ({ preload }) => { expectTrue<Equal<typeof preload, undefined>>(); return null; },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => { void params.wikiId; return null; },
    render: ({ preload }) => { expectTrue<Equal<typeof preload, null>>(); return null; },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload({ params }) { return params.wikiId.length; },
    render: ({ preload }) => { expectTrue<Equal<typeof preload, number>>(); return null; },
  },
  About: {
    preload: ({ params }) => ({ kind: "about" as const, params }),
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, { kind: "about"; params: {} }>>();
      return null;
    },
  },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => Promise.resolve(loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId })),
    render: ({ preload }) => {
      // Exact return type: the prototype does not implicitly Await the resource.
      expectTrue<Equal<typeof preload, Promise<PreloadedQuery<ViewQuery>>>>();
      return null;
    },
  },
});

declare const maybePreload: ((context: SpaRouteContext<typeof WikiRoutes.View>) => number) | undefined;
createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  // @ts-expect-error A possibly missing callback cannot supply a definite render resource.
  View: { preload: maybePreload, render: () => null },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    // @ts-expect-error Render cannot invent a resource when no preload was provided.
    render: (context: SpaRouteContext<typeof WikiRoutes.View> & { preload: number }) => null,
  },
});

// @ts-expect-error View is a required configuration entry.
createSpaRouter(WikiRoutes, otherRoutes);

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: { render: () => null },
  // @ts-expect-error Only generated route keys are accepted.
  Typo: { render: () => null },
});

createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    // @ts-expect-error Preload inputs cannot disagree with the generated path contract.
    preload: ({ params }: { params: { wikiId: number } }) => params.wikiId,
    render: () => null,
  },
});
