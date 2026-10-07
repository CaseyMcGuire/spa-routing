// HAND-WRITTEN PROTOTYPE of optional generated adapter output.
// The production generator does not emit this file.
import { PreloadPrototypeRoutes as WikiRoutes } from "../PreloadPrototypeRoutes";
import type { NoPreload, PrototypeRouter, RouteConfig } from "./adapter-contract";

// Concrete properties and separate result parameters are essential. Do not turn
// this back into a mapped type over the application's route keys.
export interface WikiRouterConfig<View, Edit, About, Section> {
  View: RouteConfig<typeof WikiRoutes.View, View>;
  Edit: RouteConfig<typeof WikiRoutes.Edit, Edit>;
  About: RouteConfig<typeof WikiRoutes.About, About>;
  Section: RouteConfig<typeof WikiRoutes.Section, Section>;
}

// Same call shape, imported from the optional application adapter.
export declare function createSpaRouter<
  View = NoPreload,
  Edit = NoPreload,
  About = NoPreload,
  Section = NoPreload,
>(
  routes: typeof WikiRoutes,
  config: WikiRouterConfig<View, Edit, About, Section>,
): PrototypeRouter;

// Alternative: the adapter binds WikiRoutes and exposes an application factory.
export declare function createWikiRouter<
  View = NoPreload,
  Edit = NoPreload,
  About = NoPreload,
  Section = NoPreload,
>(config: WikiRouterConfig<View, Edit, About, Section>): PrototypeRouter;
