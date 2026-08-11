import { useEffect, useRef, useState } from "react";
import { profileApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import { Alert, Button } from "./ui";

/** Mirrors the server's own limit, so an oversized file is refused without a round trip. */
const MAX_BYTES = 5 * 1024 * 1024;

/**
 * Picks a new profile picture and sends it to Cloudinary through the API.
 *
 * The control is hidden entirely when the server has no Cloudinary keys — an
 * upload button that can only ever produce an error message is worse than no
 * button at all. The check is one call, made once when the card mounts.
 *
 * The file input itself stays out of the layout: it cannot be styled to match
 * anything else here, so a real button drives it, exactly as the chapter audio
 * upload does.
 */
export default function AvatarUpload() {
  const { user, refresh } = useAuth();
  const inputRef = useRef(null);

  const [available, setAvailable] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    profileApi
      .avatarAvailable()
      .then((data) => {
        if (!cancelled) setAvailable(Boolean(data.available));
      })
      .catch(() => {
        if (!cancelled) setAvailable(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function handleChange(event) {
    const file = event.target.files?.[0];
    // Reset first: picking the same file twice in a row must still fire.
    event.target.value = "";
    if (!file) return;

    if (file.size > MAX_BYTES) {
      setError("Ảnh vượt quá 5 MB, vui lòng chọn ảnh nhỏ hơn.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      await profileApi.uploadAvatar(file);
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove() {
    setBusy(true);
    setError(null);
    try {
      await profileApi.removeAvatar();
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (available === false) {
    return (
      <span className="muted" style={{ fontSize: "0.82rem", textAlign: "center" }}>
        Máy chủ chưa cấu hình Cloudinary nên chưa đổi được ảnh đại diện.
      </span>
    );
  }

  return (
    <div className="stack" style={{ gap: "var(--space-2)", width: "100%" }}>
      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        onChange={handleChange}
        className="sr-only"
        tabIndex={-1}
      />

      <Button
        block
        size="sm"
        variant="primary"
        loading={busy}
        disabled={available === null}
        onClick={() => inputRef.current?.click()}
      >
        {user.avatarUrl ? "Đổi ảnh đại diện" : "Tải ảnh đại diện"}
      </Button>

      {user.avatarUrl && (
        <Button block size="sm" variant="ghost" disabled={busy} onClick={handleRemove}>
          Gỡ ảnh
        </Button>
      )}

      <span className="muted" style={{ fontSize: "0.78rem", textAlign: "center" }}>
        JPG, PNG hoặc WEBP, tối đa 5 MB.
      </span>

      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}
