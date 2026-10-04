import { useFetcher, useLoaderData } from "react-router";
import PostForm from "../components/PostForm";
import type { loadPost, savePost } from "../routeData";
import { BlogRoutes } from "../routes";

export default function EditPostPage() {
  const post = useLoaderData<typeof loadPost>();
  const save = useFetcher<typeof savePost>();
  return (
    <PostForm
      key={post.id}
      title="Edit post"
      initialValues={post}
      cancelTo={BlogRoutes.Post({ postId: post.id })}
      saving={save.state !== "idle"}
      error={save.data?.error ?? null}
      onSubmit={(input) => void save.submit(input, { method: "put", encType: "application/json" })}
    />
  );
}
