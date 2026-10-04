import { useState } from "react";
import { Link } from "react-router";
import type { WritePostRequest } from "../blog";

type Props = {
  title: string;
  initialValues: WritePostRequest;
  cancelTo: string;
  saving: boolean;
  error: string | null;
  onSubmit: (input: WritePostRequest) => void;
};

const styles = {
  form: { display: "grid", gap: "0.75rem" },
  field: { display: "grid", gap: "0.25rem" },
  actions: { display: "flex", gap: "1rem", alignItems: "center" },
};

export default function PostForm(props: Props) {
  const [title, setTitle] = useState(props.initialValues.title);
  const [body, setBody] = useState(props.initialValues.body);
  const [validationError, setValidationError] = useState<string | null>(null);

  return (
    <section>
      <h2>{props.title}</h2>
      <form
        style={styles.form}
        onSubmit={(event) => {
          event.preventDefault();
          if (props.saving) {
            return;
          }
          if (!title.trim() || !body.trim()) {
            setValidationError("Title and body must not be blank.");
            return;
          }
          setValidationError(null);
          props.onSubmit({ title: title.trim(), body });
        }}
      >
        <label style={styles.field}>
          Title
          <input required value={title} disabled={props.saving} onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label style={styles.field}>
          Body
          <textarea required rows={14} value={body} disabled={props.saving} onChange={(event) => setBody(event.target.value)} />
        </label>
        {(validationError || props.error) && <p role="alert">{validationError || props.error}</p>}
        <div style={styles.actions}>
          <button type="submit" disabled={props.saving}>{props.saving ? "Saving…" : "Save post"}</button>
          <Link to={props.cancelTo}>Cancel</Link>
        </div>
      </form>
    </section>
  );
}
