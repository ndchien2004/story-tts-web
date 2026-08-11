import { useEffect, useRef, useState } from "react";
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

function buttonClass({ variant = "default", size = "md", block, loading, className = "" }) {
  return [
    "nb-btn",
    VARIANT_CLASS[variant] ?? "",
    SIZE_CLASS[size] ?? "",
    block ? "nb-btn-block" : "",
    loading ? "nb-btn-loading" : "",
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
      className={buttonClass({ variant, size, block, loading, className })}
      // A button waiting on the network is busy, not unavailable: the class
      // keeps it looking pressable so the box does not twitch flat and back
      // again on every click.
      aria-busy={loading || undefined}
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

const REDUCED_MOTION = "(prefers-reduced-motion: reduce)";

/**
 * A number that counts to its new value instead of snapping to it.
 *
 * Totals under a story — the average score, the number of ratings, the number
 * of readers who saved it — move as a side effect of something the reader just
 * did elsewhere on the page. Teleporting from 4.2 to 4.4 at the same instant a
 * comment vanishes reads as the page rewriting itself; counting there over a
 * few hundred milliseconds reads as a consequence.
 *
 * Digits are rendered tabular so the box cannot shift width mid-count.
 *
 * @param decimals how many decimal places to hold while counting
 */
export function AnimatedNumber({ value, decimals = 0, duration = 500, className = "" }) {
  const target = Number(value) || 0;
  const [display, setDisplay] = useState(target);
  const currentRef = useRef(target);
  const frameRef = useRef(0);

  useEffect(() => {
    const from = currentRef.current;
    if (from === target) return undefined;

    // Anyone who has asked for less motion just gets the answer.
    if (window.matchMedia?.(REDUCED_MOTION).matches) {
      currentRef.current = target;
      setDisplay(target);
      return undefined;
    }

    const startedAt = performance.now();
    const step = (now) => {
      const progress = Math.min((now - startedAt) / duration, 1);
      const eased = 1 - (1 - progress) ** 3;
      currentRef.current = from + (target - from) * eased;
      setDisplay(currentRef.current);
      if (progress < 1) frameRef.current = requestAnimationFrame(step);
    };

    frameRef.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frameRef.current);
  }, [target, duration]);

  return <span className={`tabular-num ${className}`.trim()}>{display.toFixed(decimals)}</span>;
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

/**
 * Single chevron, pointing left by default.
 *
 * Paging controls read faster as a direction than as the words "Trước"/"Sau",
 * and two arrows either side of "3/7" need no translation at all.
 */
export const ChevronIcon = ({ right = false }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2.5}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    style={right ? { transform: "rotate(180deg)" } : undefined}
  >
    <path d="M15 5 8 12l7 7" />
  </svg>
);

const EyeIcon = ({ off }) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M2.5 12S6.2 5.8 12 5.8 21.5 12 21.5 12 17.8 18.2 12 18.2 2.5 12 2.5 12" />
    <circle cx="12" cy="12" r="2.9" />
    {off && <path d="m4 20 16-16" />}
  </svg>
);

/**
 * Password field with a reveal toggle.
 *
 * Typing a password blind is where sign-up forms lose people, and the button
 * costs nothing: it only ever switches the input's own `type`, so autofill and
 * the password manager keep working.
 */
export function PasswordInput({ error, ...rest }) {
  const [visible, setVisible] = useState(false);

  return (
    <span className="nb-input-affix">
      <input
        className="nb-input"
        type={visible ? "text" : "password"}
        aria-invalid={Boolean(error)}
        {...rest}
      />
      <Button
        className="nb-input-affix-btn"
        aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        title={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        aria-pressed={visible}
        onClick={() => setVisible((value) => !value)}
      >
        <EyeIcon off={visible} />
      </Button>
    </span>
  );
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

/**
 * Checkbox drawn as a slider.
 *
 * The real input stays in the DOM so focus, keyboard and assistive technology
 * keep working; only its rendering is replaced.
 */
export function Switch({ label, hint, ...rest }) {
  return (
    <label className="nb-switch">
      <input type="checkbox" {...rest} />
      <span className="nb-switch-track" aria-hidden="true" />
      <span className="nb-switch-text">
        <strong>{label}</strong>
        {hint && (
          <>
            <br />
            <span className="muted" style={{ fontSize: "0.83rem" }}>
              {hint}
            </span>
          </>
        )}
      </span>
    </label>
  );
}
