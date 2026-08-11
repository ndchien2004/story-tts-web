import { useEffect, useState } from "react";
import { authApi } from "../api/endpoints";

/** Both fall back to off, so a server that never answers simply shows neither. */
const NONE = { googleEnabled: false, googleClientId: null, passwordResetEnabled: false };

/**
 * The answer is the same for every page and never changes while the tab is
 * open, so the request is made once and its promise shared.
 */
let pending;

/**
 * Which sign-in methods this server offers.
 *
 * Returns `null` until the answer arrives — callers render nothing in that
 * moment rather than a button that might be about to disappear.
 */
export default function useAuthProviders() {
  const [providers, setProviders] = useState(null);

  useEffect(() => {
    pending ??= authApi.providers().catch(() => NONE);

    let cancelled = false;
    pending.then((data) => {
      if (!cancelled) setProviders(data);
    });

    return () => {
      cancelled = true;
    };
  }, []);

  return providers;
}
