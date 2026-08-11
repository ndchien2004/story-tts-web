import { useEffect, useRef, useState } from "react";
import { useTheme } from "../context/theme-context";

const SCRIPT_SRC = "https://accounts.google.com/gsi/client";

/** Google refuses widths outside this range, so the measured width is clamped. */
const MIN_WIDTH = 200;
const MAX_WIDTH = 400;

let scriptPromise;

/** Loads Google Identity Services once, however many buttons ask for it. */
function loadGoogleScript() {
  scriptPromise ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = () => resolve(window.google);
    script.onerror = () => {
      // Let a later attempt retry instead of failing forever on one bad load.
      scriptPromise = undefined;
      reject(new Error("Không tải được thư viện đăng nhập của Google."));
    };
    document.head.append(script);
  });

  return scriptPromise;
}

/**
 * The official Google sign-in button.
 *
 * Google renders the button itself inside the container below — their branding
 * rules do not allow a hand-drawn copy — so it is the one control on these
 * pages that does not come from `ui.jsx`.
 *
 * What comes back is an ID token; verifying it is the server's job, this
 * component only hands it over.
 *
 * @param clientId  OAuth client id, served by `GET /api/auth/providers`
 * @param text      "signin_with" on the login page, "signup_with" on the register one
 * @param onSuccess called with the credential Google issued
 * @param onError   called with a message when the widget itself fails
 */
export default function GoogleSignInButton({ clientId, text = "signin_with", onSuccess, onError }) {
  const { isDark } = useTheme();
  const containerRef = useRef(null);
  const [ready, setReady] = useState(false);

  // Held in a ref so re-rendering the parent never re-initialises the widget:
  // Google would tear down the button and it would flicker on every keystroke
  // in the form beside it.
  const handlersRef = useRef({ onSuccess, onError });
  handlersRef.current = { onSuccess, onError };

  useEffect(() => {
    if (!clientId) return undefined;

    let cancelled = false;

    loadGoogleScript()
      .then((google) => {
        if (cancelled || !containerRef.current) return;

        google.accounts.id.initialize({
          client_id: clientId,
          callback: ({ credential }) => handlersRef.current.onSuccess?.(credential),
        });

        // StrictMode mounts effects twice in development; without this the
        // second render would append a second button below the first.
        containerRef.current.replaceChildren();

        const width = Math.min(
          MAX_WIDTH,
          Math.max(MIN_WIDTH, containerRef.current.offsetWidth || MIN_WIDTH),
        );
        google.accounts.id.renderButton(containerRef.current, {
          type: "standard",
          theme: isDark ? "filled_black" : "outline",
          size: "large",
          shape: "rectangular",
          text,
          locale: "vi",
          width,
        });

        setReady(true);
      })
      .catch((error) => {
        if (!cancelled) handlersRef.current.onError?.(error.message);
      });

    return () => {
      cancelled = true;
    };
    // The theme is a dependency because Google draws the button once: switching
    // to dark mode has to make it draw a dark one.
  }, [clientId, text, isDark]);

  if (!clientId) return null;

  return (
    <div className="auth-social">
      <div ref={containerRef} className="auth-social-slot" />
      {!ready && <span className="muted auth-social-hint">Đang tải nút đăng nhập Google…</span>}
    </div>
  );
}
