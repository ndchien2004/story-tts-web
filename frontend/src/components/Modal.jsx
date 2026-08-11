import { useEffect, useRef } from "react";
import { Button } from "./ui";

const CloseIcon = () => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    aria-hidden="true"
  >
    <path d="m6 6 12 12M18 6 6 18" />
  </svg>
);

/**
 * The dialog shell: backdrop, Escape, and focus landing inside.
 *
 * Both dialogs in the console are this box with different contents — asking
 * before a deletion, and showing one record in full — so the behaviour that
 * has to be right in both lives here once.
 *
 * @param initialFocusRef element focused on open; the dialog itself otherwise
 * @param footer          row pinned under the content, for actions
 */
export default function Modal({
  open,
  title,
  role = "dialog",
  initialFocusRef,
  onClose,
  footer,
  children,
}) {
  const panelRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;

    // Focus has to leave the trigger, or Escape and the arrow keys still act
    // on the page behind the dialog.
    (initialFocusRef?.current ?? panelRef.current)?.focus();

    const onKeyDown = (event) => {
      if (event.key === "Escape") onClose?.();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose, initialFocusRef]);

  if (!open) return null;

  return (
    <div
      className="nb-modal-backdrop"
      role="presentation"
      // Only a click on the backdrop itself dismisses; clicks inside bubble up
      // to here too, hence the target check.
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose?.();
      }}
    >
      <div
        ref={panelRef}
        className="nb-modal stack"
        role={role}
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
      >
        <div className="nb-modal-head">
          <h2>{title}</h2>
          <Button className="nb-icon-btn" variant="ghost" aria-label="Đóng" onClick={onClose}>
            <CloseIcon />
          </Button>
        </div>

        <div className="nb-modal-body scroll-area">{children}</div>

        {footer && <div className="nb-modal-foot">{footer}</div>}
      </div>
    </div>
  );
}
