import { useCallback, useEffect, useState } from "react";
import { ttsApi } from "../api/endpoints";

/** A server that never answers simply offers no narration button. */
const NONE = { enabled: false, maxChars: 0 };

/**
 * Whether this reader can ask for narration, and how many goes are left today.
 *
 * Unlike the sign-in providers, this answer is per person and moves every time
 * narration is produced, so it is fetched per mount and refreshed after each
 * generation rather than shared across the tab.
 *
 * Returns `null` while the first answer is in flight — the panel shows nothing
 * then, instead of a button that may be about to disappear.
 */
export default function useTtsStatus() {
  const [status, setStatus] = useState(null);

  const refresh = useCallback(
    () =>
      ttsApi
        .status()
        .then((data) => {
          setStatus(data);
          return data;
        })
        .catch(() => {
          setStatus(NONE);
          return NONE;
        }),
    [],
  );

  useEffect(() => {
    let cancelled = false;

    ttsApi
      .status()
      .then((data) => {
        if (!cancelled) setStatus(data);
      })
      .catch(() => {
        if (!cancelled) setStatus(NONE);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return { status, refresh };
}
