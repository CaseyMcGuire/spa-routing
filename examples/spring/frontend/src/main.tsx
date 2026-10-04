import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import { createSpaRouter, createSpaRouteAuthorization } from "@spa-kit/react-router";
import BlogLayout from "./components/BlogLayout";
import MessagePage from "./components/MessagePage";
import PostListPage from "./pages/PostListPage";
import PostPage from "./pages/PostPage";
import NewPostPage from "./pages/NewPostPage";
import EditPostPage from "./pages/EditPostPage";
import RouteErrorPage from "./pages/RouteErrorPage";
import { loadPosts, loadPost, saveNewPost, savePost, removePost } from "./routeData";
import { BlogRoutes } from "./routes";
import "./blog.css";

const errorElement = <BlogLayout><RouteErrorPage /></BlogLayout>;
const hydrateFallbackElement = <BlogLayout><p role="status">Loading…</p></BlogLayout>;
const pageOptions = { errorElement, hydrateFallbackElement };

const authorize = createSpaRouteAuthorization({
  redirectMode: "router",
  onError: { type: "redirect", location: BlogRoutes.Error() },
});

const router = createSpaRouter(BlogRoutes, {
  Index: {
    ...pageOptions,
    loader: loadPosts,
    render: (_params, queryString) => (
      <BlogLayout><PostListPage query={queryString.q ?? ""} /></BlogLayout>
    ),
  },
  Post: {
    ...pageOptions,
    loader: loadPost,
    action: removePost,
    render: () => <BlogLayout><PostPage /></BlogLayout>,
  },
  NewPost: {
    ...pageOptions,
    action: saveNewPost,
    render: () => <BlogLayout><NewPostPage /></BlogLayout>,
  },
  EditPost: {
    ...pageOptions,
    loader: loadPost,
    action: savePost,
    render: () => <BlogLayout><EditPostPage /></BlogLayout>,
  },
  NotFound: {
    ...pageOptions,
    render: () => (
      <BlogLayout><MessagePage title="Post not found" message="This post does not exist or has been deleted." /></BlogLayout>
    ),
  },
  Error: {
    ...pageOptions,
    render: () => (
      <BlogLayout><MessagePage title="Something went wrong" message="The page could not be loaded. Please try again." /></BlogLayout>
    ),
  },
}, {
  sharedMiddleware: [async (args, next) => {
    // The fallback page must remain reachable when the decision endpoint fails.
    if (new URL(args.request.url).pathname === BlogRoutes.Error()) {
      await next();
      return;
    }
    await authorize(args, next);
  }],
});

const root = document.getElementById("root");
if (!root) {
  throw new Error("The blog page is missing its root element.");
}
createRoot(root).render(<RouterProvider router={router} />);
