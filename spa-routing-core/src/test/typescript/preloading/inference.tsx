import { PreloadPrototypeRoutes as WikiRoutes } from "../PreloadPrototypeRoutes";
import { createSpaRouter, createWikiRouter } from "./WikiRouter";
import type { SpaRouteContext } from "./adapter-contract";
import {
  AboutPage, EditWikiPage, EditWikiPageQuery, ViewWikiPage, ViewWikiPageQuery,
  environment, expectTrue, loadQuery,
} from "./resources";
import type { EditQuery, Equal, IsAny, PreloadedQuery, ViewQuery } from "./resources";

createSpaRouter(WikiRoutes, {
  View: {
    preload: ({ params, queryString }) => {
      expectTrue<Equal<typeof params, { wikiId: string }>>();
      expectTrue<Equal<typeof queryString.revision, string | undefined>>();
      expectTrue<Equal<typeof queryString.tag, readonly string[]>>();
      expectTrue<Equal<IsAny<typeof params>, false>>();
      expectTrue<Equal<IsAny<typeof queryString>, false>>();
      // @ts-expect-error Path parameters are strings.
      const numericId: number = params.wikiId;
      // @ts-expect-error Unknown path keys are rejected.
      params.postId;
      // @ts-expect-error Unknown query keys are rejected.
      queryString.draft;
      // @ts-expect-error Repeated query values remain readonly arrays.
      queryString.tag.push("new");
      return loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId });
    },
    render: ({ params, queryString, preload }) => {
      expectTrue<Equal<typeof preload, PreloadedQuery<ViewQuery>>>();
      expectTrue<Equal<IsAny<typeof preload>, false>>();
      expectTrue<Equal<typeof params, { wikiId: string }>>();
      expectTrue<Equal<typeof queryString.revision, string | undefined>>();
      // @ts-expect-error A view resource cannot render the editor.
      const wrongPage = <EditWikiPage queryRef={preload} />;
      // @ts-expect-error The resource does not have editor data.
      preload.data.draftBody;
      return <ViewWikiPage queryRef={preload} />;
    },
  },
  Edit: {
    preload: ({ params, queryString }) => {
      expectTrue<Equal<typeof params.wikiId, string>>();
      expectTrue<Equal<typeof queryString.draft, string>>();
      // @ts-expect-error This query key belongs to View.
      queryString.revision;
      return loadQuery(environment, EditWikiPageQuery, { wikiId: params.wikiId, draft: queryString.draft });
    },
    render: ({ params, queryString, preload }) => {
      expectTrue<Equal<typeof preload, PreloadedQuery<EditQuery>>>();
      expectTrue<Equal<IsAny<typeof preload>, false>>();
      expectTrue<Equal<typeof params.wikiId, string>>();
      expectTrue<Equal<typeof queryString.draft, string>>();
      // @ts-expect-error An edit resource cannot render the reader.
      const wrongPage = <ViewWikiPage queryRef={preload} />;
      return <EditWikiPage queryRef={preload} />;
    },
  },
  About: {
    render: (context) => {
      expectTrue<Equal<typeof context, SpaRouteContext<typeof WikiRoutes.About>>>();
      expectTrue<Equal<Extract<keyof typeof context, string>, "params" | "queryString">>();
      // @ts-expect-error The property is absent, not optional or undefined.
      context.preload;
      // @ts-expect-error A parameterless route has no wikiId.
      context.params.wikiId;
      return <AboutPage />;
    },
  },
  Section: {
    preload: ({ params, queryString }) => {
      expectTrue<Equal<typeof params.section, string | undefined>>();
      expectTrue<Equal<typeof queryString.filter, readonly string[] | undefined>>();
      return { section: params.section, filter: queryString.filter };
    },
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, { section: string | undefined; filter: readonly string[] | undefined }>>();
      return null;
    },
  },
});

// The bound application factory has the same inference, without a routes argument.
createWikiRouter({
  View: {
    preload: ({ params }) => loadQuery(environment, ViewWikiPageQuery, { wikiId: params.wikiId }),
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, PreloadedQuery<ViewQuery>>>();
      return <ViewWikiPage queryRef={preload} />;
    },
  },
  Edit: {
    preload: ({ params, queryString }) => loadQuery(environment, EditWikiPageQuery, {
      wikiId: params.wikiId,
      draft: queryString.draft,
    }),
    render: ({ preload }) => {
      expectTrue<Equal<typeof preload, PreloadedQuery<EditQuery>>>();
      return <EditWikiPage queryRef={preload} />;
    },
  },
  About: { render: ({ params, queryString }) => <AboutPage /> },
  Section: { render: ({ params, queryString }) => null },
});

// Original framework-free route builders and access metadata remain available.
expectTrue<Equal<typeof WikiRoutes.View.hasAccessHandler, boolean>>();
expectTrue<Equal<ReturnType<typeof WikiRoutes.View>, string>>();
WikiRoutes.View({ wikiId: "42" }, { tag: ["typescript"] });
// @ts-expect-error Required repeated query values cannot be a scalar.
WikiRoutes.View({ wikiId: "42" }, { tag: "typescript" });
