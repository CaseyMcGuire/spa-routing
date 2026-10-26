// Shared client route runtime. Generated copies are overwritten by generateClientRoutes.

type RouteMetadata = { applicationId: string; routeId: string; hasAccessHandler: boolean };
type PathDeclaration = { name: string; optional: boolean };
type QueryStringDeclaration = { name: string; optional: boolean; repeated: boolean };

/** One concrete destination, with its complete URL computed when the route builder is called. */
export type RouteTarget = {
  readonly href: string;
  readonly applicationId: string;
  readonly routeId: string;
};

/** Constructs a plain destination while retaining its literal application and route identity. */
export function createRouteTarget<const TMetadata extends Pick<RouteMetadata, "applicationId" | "routeId">>(
  href: string,
  metadata: TMetadata,
): RouteTarget & {
  readonly applicationId: TMetadata["applicationId"];
  readonly routeId: TMetadata["routeId"];
} {
  return { href, applicationId: metadata.applicationId, routeId: metadata.routeId };
}

// Compile-time identity only: contexts still contain just params and queryString at runtime.
declare const routeContextBrand: unique symbol;

/** Shared parser result; application code normally uses the generated per-route context aliases. */
export type ParsedRouteValues<
  TParams extends object,
  TQueryString extends object,
  TIdentity extends readonly [string, string],
> = {
  params: TParams;
  queryString: TQueryString;
  readonly [routeContextBrand]: TIdentity;
};

/** Parses decoded path values and declared query fields, returning null for invalid input. */
export function createRouteParser<
  TParams extends object,
  TQueryString extends object,
  TIdentity extends readonly [string, string],
>(
  parameters: readonly PathDeclaration[],
  queryStringDeclarations: readonly QueryStringDeclaration[],
) {
  return (
    params: Readonly<Record<string, string | undefined>>,
    search: URLSearchParams,
  ): ParsedRouteValues<TParams, TQueryString, TIdentity> | null => {
    const parsedParams: Record<string, string> = Object.create(null);
    const queryString: Record<string, string | readonly string[]> = Object.create(null);
    for (const { name, optional } of parameters) {
      const value = Object.prototype.hasOwnProperty.call(params, name) ? params[name] : undefined;
      if (value === undefined) {
        if (!optional) {
          return null;
        }
      } else {
        if (typeof value !== "string") {
          return null;
        }
        parsedParams[name] = value;
      }
    }
    for (const { name, optional, repeated } of queryStringDeclarations) {
      const values = search.getAll(name);
      if (values.length === 0) {
        if (!optional) {
          return null;
        }
      } else {
        if (!repeated && values.length !== 1) {
          return null;
        }
        queryString[name] = repeated ? values : values[0];
      }
    }
    // Validation establishes the generated route's types and opaque identity.
    return { params: parsedParams, queryString } as ParsedRouteValues<TParams, TQueryString, TIdentity>;
  };
}

/** Creates a callable route with no path or query parameters. */
export function routeWithoutParams<const TMetadata extends RouteMetadata>(path: string, metadata: TMetadata) {
  return Object.assign(() => createRouteTarget(path, metadata), {
    path,
    ...metadata,
    parse: createRouteParser<{}, {}, readonly [TMetadata["applicationId"], TMetadata["routeId"]]>([], []),
  });
}

/** Encodes one path parameter supplied to a generated URL builder. */
export function encodeRouteParam(value: string): string {
  return encodeURIComponent(value);
}

/** Creates a destination builder with path parameters, metadata, and a parser. */
export function route<TParams extends object, const TMetadata extends RouteMetadata>(
  path: string,
  buildPath: (params: TParams) => string,
  metadata: TMetadata,
  parameters: readonly PathDeclaration[],
) {
  return Object.assign((params: TParams) => createRouteTarget(buildPath(params), metadata), {
    path,
    ...metadata,
    parse: createRouteParser<TParams, {}, readonly [TMetadata["applicationId"], TMetadata["routeId"]]>(parameters, []),
  });
}

/** Validates and appends declared query-string values to a generated path. */
export function appendQueryString(
  path: string,
  queryString: object,
  declarations: readonly QueryStringDeclaration[],
): string {
  const values = queryString as Record<string, string | readonly string[] | null | undefined>;
  const search = new URLSearchParams();
  for (const { name, optional, repeated } of declarations) {
    const value = values != null && Object.prototype.hasOwnProperty.call(values, name) ? values[name] : undefined;
    if (value == null) {
      if (!optional) {
        throw new Error("Missing required query parameter: " + name);
      }
      continue;
    }
    if (repeated) {
      if (!Array.isArray(value)) {
        throw new Error("Expected a list for query parameter: " + name);
      }
      if (!optional && value.length === 0) {
        throw new Error("Required query parameter must contain a value: " + name);
      }
      for (const item of value) {
        if (typeof item !== "string") {
          throw new Error("Expected string query values: " + name);
        }
        search.append(name, item);
      }
    } else {
      if (typeof value !== "string") {
        throw new Error("Expected a string for query parameter: " + name);
      }
      search.append(name, value);
    }
  }
  const encoded = search.toString();
  return encoded ? path + "?" + encoded : path;
}

/** Reads declared query keys while preserving repeated values and their order. */
export function readQueryString<TKey extends string>(
  search: URLSearchParams,
  keys: readonly TKey[],
): Readonly<Partial<Record<TKey, readonly string[]>>> {
  const values = Object.create(null) as Partial<Record<TKey, readonly string[]>>;
  for (const key of keys) {
    if (search.has(key)) {
      values[key] = Object.freeze(search.getAll(key));
    }
  }
  return Object.freeze(values);
}
