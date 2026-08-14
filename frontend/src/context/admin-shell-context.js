import { createContext, useContext } from "react";

/**
 * Lets an admin page reach the shell around it.
 *
 * Two things live here, and both exist because the control sits in the page's
 * own top bar while the thing it moves — the sidebar — belongs to the layout:
 * the drawer toggle used on narrow screens, and the rail toggle used on wide
 * ones. `collapsed` comes along so the button can say which way it is about to
 * go.
 */
export const AdminShellContext = createContext({
  openSidebar: () => {},
  toggleRail: () => {},
  collapsed: false,
});

export const useAdminShell = () => useContext(AdminShellContext);
