import { redirect } from "react-router";
import type { ActionFunctionArgs, LoaderFunctionArgs } from "react-router";
import * as api from "./api";
import type { WritePostRequest } from "./blog";
import { BlogRoutes } from "./routes";

export async function loadPosts(args: LoaderFunctionArgs) {
  const parsed = BlogRoutes.Index.parse(args.params, new URL(args.request.url).searchParams);
  if (!parsed) {
    throw new Response("Invalid search query", { status: 400 });
  }
  return api.listPosts(parsed.queryString.q ?? "", args.request.signal);
}

export async function loadPost(args: LoaderFunctionArgs) {
  const postId = requirePostId(args);
  try {
    return await api.readPost(postId, args.request.signal);
  } catch (error) {
    if (error instanceof api.ApiError && error.status === 404) {
      throw redirect(BlogRoutes.NotFound());
    }
    throw error;
  }
}

export async function saveNewPost(args: ActionFunctionArgs) {
  try {
    const input: WritePostRequest = await args.request.json();
    const post = await api.createPost(input, args.request.signal);
    return redirect(BlogRoutes.Post({ postId: post.id }));
  } catch (error) {
    return mutationError(error, args.request.signal);
  }
}

export async function savePost(args: ActionFunctionArgs) {
  const postId = requirePostId(args);
  try {
    const input: WritePostRequest = await args.request.json();
    const post = await api.updatePost(postId, input, args.request.signal);
    return redirect(BlogRoutes.Post({ postId: post.id }));
  } catch (error) {
    return mutationError(error, args.request.signal);
  }
}

export async function removePost(args: ActionFunctionArgs) {
  const postId = requirePostId(args);
  try {
    await api.deletePost(postId, args.request.signal);
    return redirect(BlogRoutes.Index());
  } catch (error) {
    return mutationError(error, args.request.signal);
  }
}

function requirePostId(args: LoaderFunctionArgs | ActionFunctionArgs): string {
  const parsed = BlogRoutes.Post.parse(args.params, new URL(args.request.url).searchParams);
  if (!parsed) {
    throw new Response("Invalid post ID", { status: 400 });
  }
  return parsed.params.postId;
}

function mutationError(error: unknown, signal: AbortSignal) {
  signal.throwIfAborted();
  if (error instanceof api.ApiError && error.status === 404) {
    return redirect(BlogRoutes.NotFound());
  }
  if (error instanceof api.ApiError && error.status === 400) {
    return { error: "Title and body must not be blank." };
  }
  return { error: "Could not save your changes. Please try again." };
}
