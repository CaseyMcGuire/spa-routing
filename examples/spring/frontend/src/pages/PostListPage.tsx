import { useLoaderData, useNavigate } from "react-router";
import PostList from "../components/PostList";
import type { loadPosts } from "../routeData";
import { BlogRoutes } from "../routes";

type Props = {
  query: string;
};

export default function PostListPage(props: Props) {
  const posts = useLoaderData<typeof loadPosts>();
  const navigate = useNavigate();
  return (
    <PostList
      posts={{ type: "ready", value: posts }}
      query={props.query}
      onSearch={(query) => void navigate(BlogRoutes.Index(query ? { q: query } : {}))}
    />
  );
}
