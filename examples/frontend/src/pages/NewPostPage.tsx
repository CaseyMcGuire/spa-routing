import { useFetcher } from "react-router";
import PostForm from "../components/PostForm";
import type { saveNewPost } from "../routeData";
import { BlogRoutes } from "../routes";

export default function NewPostPage() {
  const save = useFetcher<typeof saveNewPost>();
  return (
    <PostForm
      title="New post"
      initialValues={{ title: "", body: "" }}
      cancelTo={BlogRoutes.Index()}
      saving={save.state !== "idle"}
      error={save.data?.error ?? null}
      onSubmit={(input) => void save.submit(input, { method: "post", encType: "application/json" })}
    />
  );
}
