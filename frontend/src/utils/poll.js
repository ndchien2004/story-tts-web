/** Long enough not to hammer the server, short enough to feel live. */
const INTERVAL_MS = 2500;

/** Narration of a long chapter takes a minute or two; this is the giving-up point. */
const TIMEOUT_MS = 5 * 60 * 1000;

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Re-runs `probe` until it reports there is nothing left to wait for.
 *
 * Narration is queued server-side and finished on a background thread, so the
 * only way to know it landed is to keep asking. The caller's `probe` is where
 * the asking happens — it fetches whatever state it cares about, updates the
 * screen with it, and answers whether the wait is over.
 *
 * @param probe        async () => boolean, true when the work has settled
 * @param isCancelled  () => boolean, so an unmounted screen stops polling
 * @returns "done" when probe said so, "timeout" when the deadline passed,
 *          "cancelled" when the caller went away
 */
export async function pollUntilSettled(probe, { isCancelled = () => false } = {}) {
  const deadline = Date.now() + TIMEOUT_MS;

  while (!isCancelled()) {
    await wait(INTERVAL_MS);
    if (isCancelled()) return "cancelled";

    if (await probe()) return "done";
    if (Date.now() > deadline) return "timeout";
  }

  return "cancelled";
}
