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

const ACCESS_LEVEL_META = {
  PUBLIC: { tone: "public", icon: "🌐", label: "Công khai" },
  MEMBER: { tone: "member", icon: "🔒", label: "Yêu cầu đăng nhập" },
  VIP: { tone: "vip", icon: "👑", label: "Yêu cầu VIP" },
};

/**
 * Badge describing a chapter's access level.
 *
 * `label` overrides the local text so the server stays the source of truth for
 * wording, while the icon and colour are decided here.
 */
export function AccessBadge({ level, label, showIcon = true }) {
  const meta = ACCESS_LEVEL_META[level] ?? ACCESS_LEVEL_META.PUBLIC;
  return (
    <Badge tone={meta.tone}>
      {showIcon && <span aria-hidden="true">{meta.icon}</span>}
      {label ?? meta.label}
    </Badge>
  );
}

/* ------------------------------------------------------------------ */
/* Feedback                                                            */
/* ------------------------------------------------------------------ */

export function Alert({ tone = "info", title, children }) {
  return (
    <div className={`nb-alert nb-alert-${tone}`} role={tone === "error" ? "alert" : "status"}>
      <div>
        {title && <strong style={{ display: "block", marginBottom: "0.2rem" }}>{title}</strong>}
        {children}
      </div>
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

export function EmptyState({ icon = "📭", title, children }) {
  return (
    <div className="empty-state">
      <div style={{ fontSize: "2.5rem" }} aria-hidden="true">
        {icon}
      </div>
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
      {hint && !error && <span className="muted" style={{ fontSize: "0.85rem" }}>{hint}</span>}
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
