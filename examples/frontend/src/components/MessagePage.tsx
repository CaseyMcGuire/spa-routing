import { Link } from "react-router";
import { BlogRoutes } from "../routes";

type Props = {
  title: string;
  message: string;
};

export default function MessagePage(props: Props) {
  return (
    <section>
      <h2>{props.title}</h2>
      <p>{props.message}</p>
      <Link to={BlogRoutes.Index()}>Back to posts</Link>
    </section>
  );
}
