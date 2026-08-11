import { createContext, useContext } from "react";

/**
 * Lets an admin page reach the shell around it.
 *
 * The only thing a page needs from the shell is the drawer toggle, which lives
 * in the page's own top bar but controls the sidebar owned by the layout.
 */
export const AdminShellContext = createContext({ openSidebar: () => {} });

export const useAdminShell = () => useContext(AdminShellContext);
