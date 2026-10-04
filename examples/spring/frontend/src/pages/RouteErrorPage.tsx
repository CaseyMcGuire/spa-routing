import { isRouteErrorResponse, useRouteError } from "react-router";
import MessagePage from "../components/MessagePage";

export default function RouteErrorPage() {
  const error = useRouteError();
  const invalidRoute = isRouteErrorResponse(error) && error.status === 400;
  return (
    <MessagePage
      title={invalidRoute ? "Invalid address" : "Something went wrong"}
      message={invalidRoute ? "Check the address and try again." : "The page could not be loaded. Please try again."}
    />
  );
}
