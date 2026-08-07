import { Link } from "react-router-dom";

/* ------------------------------------------------------------------ */
/* Button                                                              */
/* ------------------------------------------------------------------ */

const VARIANT_CLASS = {
  default: "",
  primary: "nb-btn-primary",
  info: "nb-btn-info",
  success: "nb-btn-success",
  danger: "nb-btn-danger",
  violet: "nb-btn-violet",
  ghost: "nb-btn-ghost",
};

const SIZE_CLASS = {
  sm: "nb-btn-sm",
  md: "",
  lg: "nb-btn-lg",
};

function buttonClass({ variant = "default", size = "md", block, className = "" }) {
  return [
    "nb-btn",
    VARIANT_CLASS[variant] ?? "",
    SIZE_CLASS[size] ?? "",
    block ? "nb-btn-block" : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");
}

export function Button({
  variant,
  size,
  block,
  className,
  loading = false,
  disabled,
  children,
  ...rest
}) {
  return (
    <button
      type="button"
      className={buttonClass({ variant, size, block, className })}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}

/** Router link that looks like a button. */
export function ButtonLink({ variant, size, block, className, children, ...rest }) {
  return (
    <Link className={buttonClass({ variant, size, block, className })} {...rest}>
      {children}
    </Link>
  );
}

/* ------------------------------------------------------------------ */
/* Badge                                                               */
/* ------------------------------------------------------------------ */

export function Badge({ tone = "neutral", children, className = "" }) {
  return <span className={`nb-badge nb-badge-${tone} ${className}`}>{children}</span>;
}

/**
 * Access levels are shown as plain wording, without a padlock or crown.
 * Colour alone carries the emphasis.
 */
const ACCESS_LEVEL_META = {
  PUBLIC: { tone: "public", label: "Công khai" },
  MEMBER: { tone: "member", label: "Cần đăng nhập" },
  VIP: { tone: "vip", label: "Cần VIP" },
};

/**
 * Badge describing a chapter's access level.
 *
 * `label` overrides the local wording so the server stays the source of truth,
 * while the colour is decided here.
 */
export function AccessBadge({ level, label }) {
  const meta = ACCESS_LEVEL_META[level] ?? ACCESS_LEVEL_META.PUBLIC;
  return <Badge tone={meta.tone}>{label ?? meta.label}</Badge>;
}

/* ------------------------------------------------------------------ */
/* Feedback                                                            */
/* ------------------------------------------------------------------ */

export function Alert({ tone = "info", title, children }) {
  return (
    <div className={`nb-alert nb-alert-${tone}`} role={tone === "error" ? "alert" : "status"}>
      {title && <strong>{title}</strong>}
      <div>{children}</div>
    </div>
  );
}

export function Spinner({ label = "Đang tải…" }) {
  return (
    <div className="row" style={{ justifyContent: "center", padding: "3rem 0" }}>
      <span className="spinner" aria-hidden="true" />
      <span className="muted">{label}</span>
    </div>
  );
}

export function EmptyState({ title, children }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      {children && <p className="muted">{children}</p>}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Form                                                                */
/* ------------------------------------------------------------------ */

export function Field({ label, htmlFor, error, hint, children }) {
  return (
    <div className="nb-field">
      {label && (
        <label className="nb-label" htmlFor={htmlFor}>
          {label}
        </label>
      )}
      {children}
      {hint && !error && (
        <span className="muted" style={{ fontSize: "0.83rem" }}>
          {hint}
        </span>
      )}
      {error && <span className="nb-error">{error}</span>}
    </div>
  );
}

export function TextInput({ error, ...rest }) {
  return <input className="nb-input" aria-invalid={Boolean(error)} {...rest} />;
}

export function TextArea({ error, ...rest }) {
  return <textarea className="nb-textarea" aria-invalid={Boolean(error)} {...rest} />;
}

export function Select({ children, ...rest }) {
  return (
    <select className="nb-select" {...rest}>
      {children}
    </select>
  );
}
