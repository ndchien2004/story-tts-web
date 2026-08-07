import { useRef, useState } from "react";
import { adminApi } from "../api/endpoints";
import { Button } from "./ui";

/**
 * Hidden file input driven by a styled button.
 *
 * The native input is kept out of the layout because it cannot be restyled to
 * match the rest of the controls.
 */
export default function AudioUploadButton({ chapterId, hasAudio, onUploaded, onError }) {
  const inputRef = useRef(null);
  const [uploading, setUploading] = useState(false);

  async function handleChange(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      const result = await adminApi.uploadAudio(chapterId, file);
      onUploaded?.(result);
    } catch (err) {
      onError?.(err.message);
    } finally {
      setUploading(false);
      // Reset so picking the same file again still fires a change event.
      event.target.value = "";
    }
  }

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept="audio/mpeg,audio/mp3,audio/wav,audio/ogg,audio/mp4,audio/aac"
        onChange={handleChange}
        className="sr-only"
        tabIndex={-1}
      />
      <Button
        size="sm"
        variant="violet"
        loading={uploading}
        onClick={() => inputRef.current?.click()}
        title={hasAudio ? "Tải lên bản mới, thay thế bản cũ" : "Tải lên file audio thu sẵn"}
      >
        {hasAudio ? "Thay audio" : "Tải audio"}
      </Button>
    </>
  );
}
