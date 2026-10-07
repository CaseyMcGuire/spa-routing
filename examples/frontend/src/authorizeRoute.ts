import { spaRouteContext } from "@spa-kit/react-router";
import { redirect, type MiddlewareFunction } from "react-router";
import { BlogRoutes } from "./routes";

type NavigationResult =
  | { type: "allowed" }
  | {
      type: "denied" | "unknown_route" | "invalid_request";
      destination: string;
      reason: { code: string; message: string };
    };

function isNavigationResult(value: unknown): value is NavigationResult {
  if (typeof value !== "object" || value === null || !("type" in value)) return false;
  if (value.type === "allowed") return true;
  return (value.type === "denied" || value.type === "unknown_route" || value.type === "invalid_request")
    && "destination" in value && typeof value.destination === "string" && value.destination.trim().length > 0
    && "reason" in value && typeof value.reason === "object" && value.reason !== null
    && "code" in value.reason && typeof value.reason.code === "string"
    && "message" in value.reason && typeof value.reason.message === "string";
}

// The installed spa-kit authorization helper uses the pre-0.5 HTTP-shaped payload.
export const authorizeRoute: MiddlewareFunction = async ({ context, params, request }, next) => {
  const identity = context.get(spaRouteContext);
  if (!identity) throw new Error("Missing generated route identity");
  const route = Object.values(BlogRoutes).find((route) => (
    route.applicationId === identity.applicationId && route.routeId === identity.routeId
  ));
  // The blog's application handler always allows access.
  if (route?.hasAccessHandler === false) return next();

  const url = new URL("/__spa/route-decision", request.url);
  url.searchParams.set("applicationId", identity.applicationId);
  url.searchParams.set("routeId", identity.routeId);
  Object.entries(params).forEach(([name, value]) => {
    if (value !== undefined) url.searchParams.set(`parameters.${name}`, value);
  });
  new URL(request.url).searchParams.forEach((value, name) => {
    url.searchParams.append(`queryString.${name}`, value);
  });

  let result: NavigationResult;
  try {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
      signal: request.signal,
    });
    if (!response.ok) throw new Error("Navigation check failed");
    const body: unknown = await response.json();
    if (!isNavigationResult(body)) throw new Error("Invalid navigation result");
    result = body;
  } catch (error) {
    if (request.signal.aborted) throw error;
    throw redirect(BlogRoutes.Error());
  }
  request.signal.throwIfAborted();
  if (result.type !== "allowed") throw redirect(result.destination);
  return next();
};
