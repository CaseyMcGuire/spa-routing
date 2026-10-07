import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router/dom";
import { createSpaRouter } from "@spa-kit/react-router";
import { authorizeRoute } from "./authorizeRoute";
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
  sharedMiddleware: [authorizeRoute],
});

const root = document.getElementById("root");
if (!root) {
  throw new Error("The blog page is missing its root element.");
}
createRoot(root).render(<RouterProvider router={router} />);
