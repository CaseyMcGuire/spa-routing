import { PreloadPrototypeRoutes as WikiRoutes } from "../PreloadPrototypeRoutes";
import type { AboutContext, EditContext, SectionContext, ViewContext } from "../PreloadPrototypeRoutes";
import { createSpaRouter } from "./mapped-router";
import { AboutPage, EditWikiPage, EditWikiPageQuery, ViewWikiPage, ViewWikiPageQuery, environment, expectTrue, loadQuery } from "./resources";
import type { EditQuery, Equal, IsAny, PreloadedQuery, ViewQuery } from "./resources";

createSpaRouter(WikiRoutes, {
  View: {
    // Consumer first: the explicit input annotation removes property-order sensitivity.
    render: ({ params, queryString, preload }) => {
      expectTrue<Equal<typeof params, ViewContext["params"]>>();
      expectTrue<Equal<typeof queryString, ViewContext["queryString"]>>();
      expectTrue<Equal<typeof preload, PreloadedQuery<ViewQuery>>>();
      expectTrue<Equal<IsAny<typeof preload>, false>>();
      // @ts-expect-error Another route's resource is rejected.
      const wrong = <EditWikiPage queryRef={preload} />;
      return <ViewWikiPage queryRef={preload} />;
    },
    preload: ({ params, queryString }: ViewContext) => {
      expectTrue<Equal<typeof params.wikiId, string>>();
      expectTrue<Equal<typeof queryString.revision, string | undefined>>();
      expectTrue<Equal<typeof queryString.tag, readonly string[]>>();
      // @ts-expect-error Unknown path keys are rejected.
      params.postId;
      // @ts-expect-error Required repeated query values remain arrays.
      const tag: string = queryString.tag;
      return loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId });
    },
  },
  Edit: {
    preload: ({ params, queryString }: EditContext) =>
      loadQuery(environment, EditWikiPageQuery, { wikiId: params.wikiId, draft: queryString.draft }),
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, PreloadedQuery<EditQuery>>>();
      // @ts-expect-error Each resource type remains independent.
      const wrong = <ViewWikiPage queryRef={preload} />;
      return <EditWikiPage queryRef={preload} />;
    },
  },
  Section: {
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, { section: string | undefined; filter: readonly string[] | undefined }>>();
      return null;
    },
    preload: ({ params, queryString }: SectionContext) => ({ section: params.section, filter: queryString.filter }),
  },
  About: {
    render: context => {
      expectTrue<Equal<typeof context, AboutContext>>();
      // @ts-expect-error Non-preloading routes have no preload property.
      context.preload;
      return <AboutPage />;
    },
  },
});

const otherRoutes = { Edit: { render: () => null }, Section: { render: () => null }, About: { render: () => null } };
createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    // @ts-expect-error The context for a different route is rejected at the preload.
    preload: ({ params }: EditContext) => params.wikiId,
    render: () => null,
  },
});

// Pin the actual failure when the input annotation is forgotten. The lint rule
// should report it at the parameter, before this secondary render error appears.
createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    preload: ({ params }) => loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId }),
    render: context => {
      expectTrue<Equal<typeof context, ViewContext>>();
      // @ts-expect-error Failed inference leaves no typed preload in this mapped signature.
      return <ViewWikiPage queryRef={context.preload} />;
    },
  },
});

// A callback with no input does not need an annotation.
createSpaRouter(WikiRoutes, {
  ...otherRoutes,
  View: {
    render: ({ preload }) => { expectTrue<Equal<typeof preload, number>>(); return null; },
    preload: () => 42,
  },
});
