import { useCallback, useEffect, useRef, useState } from "react";
import { adminApi } from "../../api/endpoints";
import { pollUntilSettled } from "../../utils/poll";
import AudioPreview from "./AudioPreview";
import AudioUploadButton from "../AudioUploadButton";
import ConfirmDialog from "../ConfirmDialog";
import { Alert, Badge, Button, EmptyState, Select, Spinner } from "../ui";

const SOURCE_LABEL = {
  UPLOAD: "Bản thu tải lên",
  TTS: "Giọng đọc máy",
};

const STATUS_TONE = {
  READY: "public",
  PROCESSING: "info",
  FAILED: "danger",
};

const STATUS_LABEL = {
  READY: "Dùng được",
  PROCESSING: "Đang tạo",
  FAILED: "Hỏng",
};

/**
 * Audio of one chapter, from the admin side.
 *
 * Deliberately not the reader's list: this one shows failed tracks too, since
 * whoever has to clear one out is the only person who needs to see it.
 *
 * Generating goes through the batch endpoint with a list of one. A second
 * single-chapter endpoint would have to repeat its access checks and its cap,
 * and would then be a second way for narration to be produced — the batch route
 * is the only one, on purpose.
 */
export default function ChapterAudioPanel({ chapterId, chapterTitle, dirty, onSaveFirst }) {
  const [tracks, setTracks] = useState([]);
  const [voices, setVoices] = useState([]);
  const [voice, setVoice] = useState("");

  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Polling outlives a render but must not outlive the screen.
  const gone = useRef(false);
  useEffect(() => {
    gone.current = false;
    return () => {
      gone.current = true;
    };
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    adminApi
      .chapterAudio(chapterId)
      .then((data) => {
        setTracks(data);
        setError(null);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [chapterId]);

  useEffect(load, [load]);

  useEffect(() => {
    adminApi
      .ttsVoices()
      .then((data) => setVoices(data ?? []))
      .catch(() => setVoices([]));
  }, []);

  /**
   * Asks for narration and stays on the line until it lands.
   *
   * The server answers the moment the job is queued, not when it is done, so
   * the button would otherwise stop spinning while nothing had been produced
   * yet. Polling here means the panel shows the finished track — ready to play
   * — instead of asking the admin to come back and reload.
   */
  async function generate() {
    setGenerating(true);
    setError(null);
    setNotice(null);

    try {
      // Narration is built from the chapter in the database. Edits still only
      // in the editor would be read straight past, and the result would sound
      // right while being a recording of the previous draft.
      if (dirty) {
        await onSaveFirst();
      }

      const [result] = await adminApi.batchTts({
        chapterIds: [chapterId],
        voice: voice || undefined,
      });

      if (!result?.queued) {
        setError(result?.message ?? "Không xếp hàng tạo audio được.");
        return;
      }

      let latest = await refreshTracks();

      const outcome = await pollUntilSettled(
        async () => {
          latest = await refreshTracks();
          return !latest.some((track) => track.status === "PROCESSING");
        },
        { isCancelled: () => gone.current },
      );

      if (gone.current) return;

      if (outcome === "timeout") {
        setError("Quá thời gian chờ tạo audio. Bản đang tạo vẫn có thể xong sau, hãy tải lại trang.");
        return;
      }

      const failed = latest.find((track) => track.status === "FAILED");
      if (latest.some((track) => track.status === "READY")) {
        setNotice("Đã tạo xong audio. Bấm nút phát bên dưới để nghe thử.");
      } else if (failed) {
        setError(failed.errorMessage ?? "Tạo audio không thành công.");
      }
    } catch (err) {
      setError(err.message);
    } finally {
      if (!gone.current) setGenerating(false);
    }
  }

  /** Re-reads the track list and puts it on screen; returns it for the caller. */
  async function refreshTracks() {
    const data = await adminApi.chapterAudio(chapterId);
    if (!gone.current) setTracks(data);
    return data;
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await adminApi.deleteAudio(pendingDelete.id);
      setNotice("Đã xóa bản audio.");
      setPendingDelete(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="stack" style={{ gap: "var(--space-3)" }}>
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {loading && <Spinner label="Đang tải audio…" />}

      {!loading && tracks.length === 0 && (
        <EmptyState title="Chương chưa có audio">
          Tải lên bản thu sẵn, hoặc để máy chủ tạo giọng đọc từ nội dung chương.
        </EmptyState>
      )}

      {!loading && tracks.length > 0 && (
        <ul className="admin-audio-list">
          {tracks.map((track) => (
            <li key={track.id} className="admin-audio-item">
              <span className="admin-audio-meta">
                <strong>{SOURCE_LABEL[track.source] ?? track.source}</strong>
                <Badge tone={STATUS_TONE[track.status] ?? "neutral"}>
                  {STATUS_LABEL[track.status] ?? track.status}
                </Badge>
                {track.status === "PROCESSING" && (
                  <span className="spinner" aria-hidden="true" />
                )}
              </span>

              {track.errorMessage && <span className="nb-error">{track.errorMessage}</span>}

              <Button size="sm" variant="danger" onClick={() => setPendingDelete(track)}>
                Xóa
              </Button>

              {/* Full width under the row: the native player is wider than the
                  label beside it and would squash everything else onto two
                  lines if it shared the line. */}
              <AudioPreview streamUrl={track.streamUrl} />
            </li>
          ))}
        </ul>
      )}

      <div className="row" style={{ gap: "var(--space-2)", flexWrap: "wrap" }}>
        <AudioUploadButton
          chapterId={chapterId}
          hasAudio={tracks.length > 0}
          onUploaded={() => {
            setNotice("Đã tải lên bản audio mới.");
            load();
          }}
          onError={setError}
        />

        {/* The label says it saves, because it does: narration is built from
            the stored chapter, so generating without saving first would read
            out the previous draft. */}
        <Button
          size="sm"
          variant="violet"
          loading={generating}
          title={
            dirty
              ? "Lưu nội dung đang sửa rồi mới dựng giọng đọc"
              : "Dựng giọng đọc từ nội dung chương"
          }
          onClick={generate}
        >
          {generating ? "Đang tạo audio…" : dirty ? "Lưu và tạo audio" : "Tạo audio"}
        </Button>
      </div>

      {dirty && !generating && (
        <Alert tone="warning">
          Chương đang có thay đổi chưa lưu. Audio được dựng từ bản đã lưu trên máy chủ, nên nút trên
          sẽ lưu nội dung trước rồi mới tạo.
        </Alert>
      )}

      {generating && (
        <Alert tone="info">
          Đang dựng giọng đọc cho chương này. Chương dài mất một vài phút — cứ để tab này mở, xong là
          bản audio hiện ra ngay bên trên.
        </Alert>
      )}

      {voices.length > 0 && (
        <Select
          aria-label="Giọng đọc dùng khi tạo audio"
          value={voice}
          onChange={(event) => setVoice(event.target.value)}
        >
          {/* Bỏ trống nghĩa là dùng ELEVENLABS_VOICE_ID trong .env. Nhãn nói rõ
              là ".env" chứ không phải "của máy chủ" chung chung: trước kia mục
              này rơi vào giọng đứng đầu danh sách nhà cung cấp, tức một giọng
              không ai cấu hình và có thể đổi bất cứ lúc nào. */}
          <option value="">Giọng mặc định trong .env</option>
          {voices.map((option) => (
            <option key={option.code} value={option.code}>
              {option.name}
              {option.gender ? ` — ${option.gender}` : ""}
            </option>
          ))}
        </Select>
      )}

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa bản audio?"
        message={`Bản audio của chương “${chapterTitle}” sẽ bị xóa.`}
        detail="File trên đĩa cũng bị xóa theo, không khôi phục được."
        confirmLabel="Xóa audio"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
