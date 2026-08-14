import { createContext, useContext } from "react";

/**
 * How an admin screen says "that worked".
 *
 * One function, deliberately. Every confirmation in the console is the same
 * shape — a past-tense sentence about something that has already happened, read
 * once and never again — so there is nothing for a page to configure: no tone,
 * no duration, no dismissal. Anything that needs to stay on screen and be acted
 * on is not this; it is an `<Alert>` in the page, where it can be read at
 * leisure and where the failing form is still in front of the reader.
 *
 * The default is a no-op so a page rendered outside the console (a test, a
 * story) still runs rather than throwing on a missing provider.
 *
 * @see AdminToaster for what it puts on screen
 */
export const AdminToastContext = createContext(() => {});

/** @returns {(message: string) => void} */
export const useAdminToast = () => useContext(AdminToastContext);
