import { useRef } from "react";
import Modal from "./Modal";
import { Button } from "./ui";

/**
 * Confirmation dialog for destructive admin actions.
 *
 * `window.confirm` blocks the whole tab, cannot say which item is affected in
 * anything but plain text, and looks nothing like the rest of the console — so
 * deletions ask here instead. Escape and the backdrop both cancel, and focus
 * lands on the safe choice rather than the destructive one.
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  detail,
  confirmLabel = "Xóa",
  cancelLabel = "Hủy",
  busy = false,
  onConfirm,
  onCancel,
}) {
  const cancelRef = useRef(null);

  return (
    <Modal
      open={open}
      title={title}
      role="alertdialog"
      initialFocusRef={cancelRef}
      onClose={onCancel}
      footer={
        <>
          <Button ref={cancelRef} onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button variant="danger" loading={busy} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p>{message}</p>
      {detail && (
        <p className="muted" style={{ marginTop: "var(--space-2)" }}>
          {detail}
        </p>
      )}
    </Modal>
  );
}
