import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { adminApi, storyApi } from "../../api/endpoints";
import { pollUntilSettled } from "../../utils/poll";
import { formatDateTime } from "../../utils/format";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import AudioPreview from "../../components/admin/AudioPreview";
import AudioUploadButton from "../../components/AudioUploadButton";
import ConfirmDialog from "../../components/ConfirmDialog";
import Pagination from "../../components/Pagination";
import {
  Alert,
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  FilterChips,
  SearchInput,
  Select,
  Spinner,
} from "../../components/ui";

const ACCESS_LEVELS = [
  { value: "PUBLIC", label: "Công khai" },
  { value: "MEMBER", label: "Yêu cầu đăng nhập" },
  { value: "VIP", label: "Yêu cầu VIP" },
];

/**
 * Ô giá Xu sửa được ngay trên dòng.
 *
 * <p>Ghi khi rời ô hoặc khi bấm Enter, không ghi theo từng phím: gõ "150" mà ghi
 * mỗi ký tự là ba lần lưu, và lần đầu tiên đặt giá chương thành 1 Xu.
 *
 * <p>Giữ giá trị đang gõ trong trạng thái riêng thay vì đọc thẳng từ dòng dữ
 * liệu, vì trong lúc gõ hai thứ ấy cố ý lệch nhau. Đồng bộ lại khi máy chủ trả
 * về giá mới — kể cả khi lệnh ghi hỏng, để ô không hiện một con số chưa bao giờ
 * được lưu.
 */
function PriceCell({ chapter, disabled, onCommit }) {
  const saved = chapter.coinPrice ?? 0;
  const [value, setValue] = useState(String(saved));

  useEffect(() => {
    setValue(String(saved));
  }, [saved]);

  // Chương công khai ai cũng đọc được rồi, nên một cái giá gắn lên đó là hai câu
  // mâu thuẫn nhau. Máy chủ từ chối, và ô này nói trước điều đó.
  const locked = chapter.accessLevel === "PUBLIC";

  function commit() {
    const next = Math.max(Number(value) || 0, 0);
    if (next === saved) {
      setValue(String(saved));
      return;
    }
    onCommit(next);
  }

  return (
    <input
      type="number"
      min="0"
      step="10"
      className="nb-input admin-price-input"
      aria-label={`Giá Xu của chương ${chapter.chapterNumber}`}
      title={
        locked
          ? "Chương công khai không đặt giá được — đổi mức khóa trước."
          : "0 = không bán lẻ. VIP luôn đọc miễn phí."
      }
      disabled={disabled || locked}
      value={locked ? "" : value}
      placeholder={locked ? "—" : "0"}
      onChange={(event) => setValue(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          event.currentTarget.blur();
        }
      }}
    />
  );
}

const AUDIO_FILTERS = [
  { value: "", label: "Mọi tình trạng audio" },
  { value: "missing", label: "Chưa có audio" },
  { value: "has", label: "Đã có audio" },
];

/*
 * "Chương nào đang bán bằng Xu" là câu hỏi không trả lời được bằng cách nhìn
 * cột mức khóa — giá là một trục riêng. Bộ lọc này là chỗ trả lời nó.
 */
const PRICE_FILTERS = [
  { value: "", label: "Mọi mức giá" },
  { value: "paid", label: "Đang bán bằng Xu" },
  { value: "free", label: "Không bán lẻ" },
];

/** Mirrors MAX_BATCH on the server; selecting more is refused there anyway. */
const MAX_BATCH = 20;

/** The server clamps a page to this many rows, so asking for more buys nothing. */
const AUDIO_STATUS_PAGE_SIZE = 100;

/**
 * Số chương mỗi trang ở khu quản trị — trần cứng của máy chủ.
 *
 * Rộng gấp đôi trang người đọc vì mọi thao tác hàng loạt ở đây làm trên những
 * dòng đang thấy: trang càng hẹp thì một lần đặt giá cho nửa sau của truyện
 * càng phải lặp lại nhiều lượt.
 */
const CHAPTER_PAGE_SIZE = 200;

/**
 * Audio status for every chapter of a story, however long the story is.
 *
 * Paged rather than fetched in one go because the endpoint refuses to return
 * more than a hundred rows at a time — asking for the lot and trusting the
 * answer would quietly mark chapter 101 onwards as having no audio.
 */
async function fetchAudioStatus(storyId) {
  const rows = [];
  let page = 0;
  let totalPages = 1;

  do {
    const data = await adminApi.listChapterAudio({
      storyId,
      page,
      size: AUDIO_STATUS_PAGE_SIZE,
    });
    rows.push(...(data.content ?? []));
    totalPages = data.totalPages ?? 1;
    page += 1;
  } while (page < totalPages);

  return rows;
}

/**
 * Everything that happens to one story's chapters, audio included.
 *
 * Audio used to have a console tab of its own, listing every chapter of every
 * story. That view answered "what is missing" but detached the answer from the
 * chapter it belonged to: fixing something meant finding the same chapter again
 * somewhere else. It lives here now, beside the chapter it describes, and the
 * "what is missing" question survives as a filter.
 *
 * Two requests feed the table. The story detail carries the chapters; the audio
 * status of each one comes separately, because whether a track is merely queued
 * or has already failed is not something the reader's view of a story knows or
 * should carry.
 */
export default function AdminChaptersPage() {
  const { storyId } = useParams();

  const [story, setStory] = useState(null);
  const [chapters, setChapters] = useState([]);
  /** Thông tin trang hiện tại: tổng số chương, số trang, đang ở trang mấy. */
  const [page, setPage] = useState(null);
  const [audioStatus, setAudioStatus] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const notify = useAdminToast();
  const [savingId, setSavingId] = useState(null);

  const [filter, setFilter] = useState("");
  const [audioFilter, setAudioFilter] = useState("");
  const [priceFilter, setPriceFilter] = useState("");

  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Bulk selection. Kept as ids rather than as a flag on each row so the filter
  // above can change under it without the ticks going missing.
  const [selected, setSelected] = useState([]);
  const [bulkLevel, setBulkLevel] = useState("PUBLIC");
  const [bulkSaving, setBulkSaving] = useState(false);
  const [bulkPrice, setBulkPrice] = useState("0");
  const [bulkPricing, setBulkPricing] = useState(false);

  const [voices, setVoices] = useState([]);
  const [voice, setVoice] = useState("");
  const [generating, setGenerating] = useState(false);
  const [batchResults, setBatchResults] = useState(null);

  /** Which chapter is showing its preview player; only one at a time. */
  const [previewing, setPreviewing] = useState(null);

  // Polling outlives a render but must not outlive the screen.
  const gone = useRef(false);
  useEffect(() => {
    gone.current = false;
    return () => {
      gone.current = true;
    };
  }, []);

  /*
   * Chương về theo trang, không còn về cả nghìn dòng một lượt.
   *
   * Trang chi tiết truyện chỉ còn dùng để lấy phần thông tin truyện; danh sách
   * chương đi bằng đường riêng vì khu quản trị cần trang rộng hơn hẳn trang
   * người đọc — mọi thao tác hàng loạt ở dưới đều làm trên những dòng đang thấy.
   */
  const load = useCallback(
    (nextPage = 0) => {
      setLoading(true);
      Promise.all([
        storyApi.detail(storyId),
        storyApi.chapters(storyId, { page: nextPage, size: CHAPTER_PAGE_SIZE }),
        // Losing the status only costs the badges; the chapter list itself is
        // still worth showing, so this failure is swallowed rather than raised.
        fetchAudioStatus(storyId).catch(() => []),
      ])
        .then(([detail, chapterPage, audioRows]) => {
          setStory(detail.story);
          setChapters(chapterPage.content);
          setPage(chapterPage);
          setAudioStatus(Object.fromEntries(audioRows.map((row) => [row.chapterId, row])));
          setError(null);
        })
        .catch((err) => setError(err.message))
        .finally(() => setLoading(false));
    },
    [storyId],
  );

  useEffect(() => {
    load(0);
  }, [load]);

  // The voice list comes from whichever provider is configured, so it cannot be
  // hard-coded. No provider means no list, and the selector stays hidden.
  useEffect(() => {
    adminApi
      .ttsVoices()
      .then((data) => setVoices(data ?? []))
      .catch(() => setVoices([]));
  }, []);

  /** Inline access-level change; updates just the affected row on success. */
  async function handleAccessLevelChange(chapter, accessLevel) {
    setSavingId(chapter.id);
    setError(null);

    try {
      const updated = await adminApi.setChapterAccessLevel(chapter.id, accessLevel);
      setChapters((current) =>
        current.map((row) => (row.id === chapter.id ? { ...row, ...updated } : row)),
      );
      notify(`Đã đổi mức khóa của “${chapter.title}”.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingId(null);
    }
  }

  /**
   * Đặt giá Xu cho một chương, ngay trên dòng của nó.
   *
   * <p>Đi bằng lệnh gọi riêng chứ không kèm vào lệnh đổi mức khóa: hai thứ trả
   * lời hai câu khác nhau, và máy chủ cũng tách chúng ra ở hai endpoint vì lý do
   * ấy. Gộp lại thì mỗi lần đổi mức khóa lại gửi kèm một cái giá không ai chạm
   * vào — và giá dương trên chương công khai bị từ chối.
   */
  async function handlePriceChange(chapter, coinPrice) {
    if (coinPrice === chapter.coinPrice) return;

    setSavingId(chapter.id);
    setError(null);

    try {
      const updated = await adminApi.setChapterPrice(chapter.id, coinPrice);
      setChapters((current) =>
        current.map((row) => (row.id === chapter.id ? { ...row, ...updated } : row)),
      );
      notify(
        coinPrice > 0
          ? `“${chapter.title}” giờ mở khóa bằng ${coinPrice.toLocaleString("vi-VN")} Xu.`
          : `“${chapter.title}” không còn bán lẻ bằng Xu.`,
      );
    } catch (err) {
      setError(err.message);
      // Ghi đè lại bằng giá trị máy chủ đang giữ, để ô nhập không hiện một con
      // số chưa bao giờ được lưu.
      setChapters((current) => [...current]);
    } finally {
      setSavingId(null);
    }
  }

  function toggleSelected(chapterId) {
    setSelected((current) =>
      current.includes(chapterId)
        ? current.filter((id) => id !== chapterId)
        : [...current, chapterId],
    );
  }

  /**
   * Sets one access level across every ticked chapter in a single request.
   *
   * One request rather than a loop of them: the server does the whole list in
   * one transaction, so a story can never be left half-locked in a way nobody
   * could tell apart from a deliberate arrangement.
   */
  async function applyBulkLevel() {
    setBulkSaving(true);
    setError(null);
    try {
      const { updated } = await adminApi.setChapterAccessLevelBulk(selected, bulkLevel);
      const label = ACCESS_LEVELS.find((level) => level.value === bulkLevel)?.label ?? bulkLevel;
      notify(`Đã đổi mức khóa của ${updated} chương thành “${label}”.`);
      setSelected([]);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBulkSaving(false);
    }
  }

  /**
   * Đặt cùng một giá cho mọi chương đã tick.
   *
   * <p>Một request chứ không phải một vòng lặp, cùng lý do với việc đổi mức khóa
   * hàng loạt: máy chủ làm cả danh sách trong một giao dịch, nên không có cách
   * nào để lại một truyện đặt giá dở dang mà nhìn vào không phân biệt được với
   * một sắp xếp cố ý.
   */
  async function applyBulkPrice() {
    setBulkPricing(true);
    setError(null);
    try {
      const price = Number(bulkPrice) || 0;
      const { updated } = await adminApi.setChapterPriceBulk(selected, price);
      notify(
        price > 0
          ? `Đã đặt giá ${price.toLocaleString("vi-VN")} Xu cho ${updated} chương.`
          : `Đã bỏ giá bán lẻ của ${updated} chương.`,
      );
      setSelected([]);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBulkPricing(false);
    }
  }

  /**
   * Queues narration for the ticked chapters, then waits for it.
   *
   * The server answers as soon as the jobs are queued, so stopping there would
   * leave the button still and the rows saying "Đang tạo…" with nothing to say
   * when that changed. Polling keeps the badges moving and turns them into
   * "Đã có" — with a play button — the moment each chapter lands.
   */
  async function generateAudio() {
    const queuedIds = selected;

    setGenerating(true);
    setBatchResults(null);
    setError(null);

    try {
      const results = await adminApi.batchTts({
        chapterIds: queuedIds,
        voice: voice || undefined,
      });
      setBatchResults(results);
      setSelected([]);

      const accepted = results.filter((entry) => entry.queued).map((entry) => entry.chapterId);
      if (accepted.length === 0) return;

      const outcome = await pollUntilSettled(
        async () => {
          const rows = await fetchAudioStatus(storyId);
          if (gone.current) return true;
          setAudioStatus(Object.fromEntries(rows.map((row) => [row.chapterId, row])));
          return accepted.every((id) => !rows.find((row) => row.chapterId === id)?.processing);
        },
        { isCancelled: () => gone.current },
      );

      if (gone.current) return;

      if (outcome === "timeout") {
        setError("Quá thời gian chờ tạo audio. Việc tạo vẫn chạy ở máy chủ, hãy tải lại trang sau.");
      } else {
        notify(`Đã tạo xong audio cho ${accepted.length} chương. Bấm “Nghe thử” để kiểm tra.`);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      if (!gone.current) setGenerating(false);
    }
  }

  async function confirmDelete() {
    setDeleting(true);
    try {
      await adminApi.deleteChapter(pendingDelete.id);
      notify(`Đã xóa chương “${pendingDelete.title}”.`);
      setPendingDelete(null);
      load();
    } catch (err) {
      setError(err.message);
      setPendingDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  // Filtering happens client-side: the whole chapter list already arrived with
  // the story, so a round trip per keystroke would buy nothing.
  const visibleChapters = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return chapters.filter((chapter) => {
      if (
        needle &&
        !chapter.title.toLowerCase().includes(needle) &&
        !String(chapter.chapterNumber).includes(needle)
      ) {
        return false;
      }
      if (priceFilter) {
        const paid = (chapter.coinPrice ?? 0) > 0;
        if (priceFilter === "paid" ? !paid : paid) return false;
      }

      if (!audioFilter) return true;
      const hasAudio = audioStatus[chapter.id]?.hasAudio ?? chapter.hasAudio;
      return audioFilter === "has" ? hasAudio : !hasAudio;
    });
  }, [chapters, filter, audioFilter, priceFilter, audioStatus]);

  /*
   * Chương công khai không đặt giá được, và máy chủ làm cả lô trong một giao
   * dịch — nên một chương công khai lẫn vào sẽ làm hỏng cả lệnh. Biết trước thì
   * nút tự tắt, thay vì để người dùng bấm rồi nhận một lỗi không nói rõ chương nào.
   */
  const bulkPriceBlocked = useMemo(() => {
    if ((Number(bulkPrice) || 0) <= 0) return false;
    return chapters.some(
      (chapter) => selected.includes(chapter.id) && chapter.accessLevel === "PUBLIC",
    );
  }, [chapters, selected, bulkPrice]);

  // "Select all" means all of what is on screen, not all of what exists — with
  // a filter applied, the other reading would tick rows nobody can see.
  const allVisibleSelected =
    visibleChapters.length > 0 &&
    visibleChapters.every((chapter) => selected.includes(chapter.id));

  function toggleAllVisible() {
    setSelected(allVisibleSelected ? [] : visibleChapters.map((chapter) => chapter.id));
  }

  const audioCount = chapters.filter(
    (chapter) => audioStatus[chapter.id]?.hasAudio ?? chapter.hasAudio,
  ).length;

  const queued = batchResults?.filter((entry) => entry.queued).length ?? 0;

  if (loading) {
    return (
      <AdminPage crumbs={[{ to: "/admin/truyen", label: "Truyện" }]} title="Đang tải…">
        <Spinner />
      </AdminPage>
    );
  }

  return (
    <AdminPage
      /* No "Chương" crumb of its own: the trail ends on the story's name, and
         with the page head gone that trail is the only place it is written. */
      crumbs={[{ to: "/admin/truyen", label: "Truyện" }]}
      title={story?.title ?? "Quản lý chương"}
      actions={
        <ButtonLink to={`/admin/truyen/${storyId}/chuong/moi`} variant="primary">
          + Thêm chương
        </ButtonLink>
      }
    >
      {error && <Alert tone="error">{error}</Alert>}

      {batchResults && (
        <Alert tone={queued === batchResults.length ? "success" : "warning"}>
          <strong>
            Đã xếp hàng {queued}/{batchResults.length} chương.
          </strong>
          <ul className="admin-batch-report">
            {batchResults.map((entry) => (
              <li key={entry.chapterId}>
                {entry.queued ? "✓" : "✕"} {entry.chapterTitle} — {entry.message}
              </li>
            ))}
          </ul>
          Audio được tạo ở luồng nền, tải lại trang sau ít phút để thấy kết quả.
        </Alert>
      )}

      <div className="admin-split">
        {/* Context column: what is being edited, and the actions that apply to
            the story as a whole rather than to one chapter. */}
        <aside className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Thông tin truyện</span>
          </div>

          <div className="admin-panel-body admin-panel-body-pad scroll-area">
            <div className="stack" style={{ gap: "var(--space-4)" }}>
              {story?.coverImage ? (
                <img className="admin-cover-preview" src={story.coverImage} alt="" />
              ) : (
                <div
                  className="admin-cover-preview"
                  style={{ display: "grid", placeItems: "center", background: "var(--bg-alt)" }}
                >
                  <span style={{ fontSize: "2.4rem", fontWeight: 600, color: "var(--ink-muted)" }}>
                    {story?.title?.slice(0, 1).toUpperCase()}
                  </span>
                </div>
              )}

              <dl className="admin-meta-list">
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Tác giả</dt>
                  <dd className="admin-meta-value">{story?.author?.name ?? "—"}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Thể loại</dt>
                  <dd className="admin-meta-value">{story?.genre?.name ?? "—"}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Trạng thái</dt>
                  <dd className="admin-meta-value">
                    <Badge tone={story?.status === "COMPLETED" ? "public" : "neutral"}>
                      {story?.statusLabel}
                    </Badge>
                  </dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Số chương</dt>
                  <dd className="admin-meta-value">{chapters.length}</dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Đã có audio</dt>
                  <dd className="admin-meta-value">
                    {audioCount}/{chapters.length}
                  </dd>
                </div>
                <div className="admin-meta-row">
                  <dt className="admin-meta-key">Lượt xem</dt>
                  <dd className="admin-meta-value">
                    {(story?.viewCount ?? 0).toLocaleString("vi-VN")}
                  </dd>
                </div>
              </dl>

              <div className="stack" style={{ gap: "var(--space-2)" }}>
                <ButtonLink to={`/admin/truyen/${storyId}`} block>
                  Sửa thông tin truyện
                </ButtonLink>
              </div>
            </div>
          </div>
        </aside>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Danh sách chương</span>
            <span className="admin-stat">
              <b>{chapters.length}</b>
              <span>chương</span>
            </span>
          </div>

          {/* Three states of audio, shown as three chips rather than hidden in
              a select: they are the answer to "what can this screen show me",
              and finding the chapters without audio is the reason most visits
              to this screen happen at all. */}
          <div className="admin-toolbar">
            <SearchInput
              className="admin-search"
              aria-label="Lọc chương"
              placeholder="Lọc theo tiêu đề hoặc số chương…"
              value={filter}
              onChange={(event) => setFilter(event.target.value)}
            />

            <FilterChips
              label="Lọc theo tình trạng audio"
              options={AUDIO_FILTERS}
              value={audioFilter}
              onChange={setAudioFilter}
            />

            <FilterChips
              label="Lọc theo giá Xu"
              options={PRICE_FILTERS}
              value={priceFilter}
              onChange={setPriceFilter}
            />
          </div>

          {/* Only appears once rows are ticked; an empty toolbar above every
              list is noise. */}
          {selected.length > 0 && (
            <div className="admin-bulkbar">
              <strong>Đã chọn {selected.length} chương</strong>

              <Select
                aria-label="Đặt mức khóa cho các chương đã chọn"
                value={bulkLevel}
                onChange={(event) => setBulkLevel(event.target.value)}
              >
                {ACCESS_LEVELS.map((level) => (
                  <option key={level.value} value={level.value}>
                    {level.label}
                  </option>
                ))}
              </Select>

              <Button variant="primary" loading={bulkSaving} onClick={applyBulkLevel}>
                Đổi mức khóa
              </Button>

              {/* Đặt giá cho cả lô — cách duy nhất hợp lý để khóa nửa sau của
                  một truyện hai trăm chương. */}
              <input
                type="number"
                min="0"
                step="10"
                className="nb-input admin-price-input"
                aria-label="Giá Xu cho các chương đã chọn"
                value={bulkPrice}
                onChange={(event) => setBulkPrice(event.target.value)}
              />

              <Button
                variant="primary"
                loading={bulkPricing}
                disabled={bulkPriceBlocked}
                title={
                  bulkPriceBlocked
                    ? "Trong danh sách đã chọn có chương công khai — chương công khai không đặt giá được."
                    : "Đặt giá Xu cho các chương đã chọn"
                }
                onClick={applyBulkPrice}
              >
                Đặt giá Xu
              </Button>

              {voices.length > 0 && (
                <Select
                  aria-label="Giọng đọc dùng cho lô này"
                  value={voice}
                  onChange={(event) => setVoice(event.target.value)}
                >
                  <option value="">Giọng mặc định của máy chủ</option>
                  {voices.map((option) => (
                    <option key={option.code} value={option.code}>
                      {option.name}
                      {option.gender ? ` — ${option.gender}` : ""}
                    </option>
                  ))}
                </Select>
              )}

              <Button
                variant="violet"
                loading={generating}
                disabled={selected.length > MAX_BATCH}
                title={
                  selected.length > MAX_BATCH
                    ? `Mỗi lượt tạo tối đa ${MAX_BATCH} chương`
                    : "Tạo audio cho các chương đã chọn"
                }
                onClick={generateAudio}
              >
                Tạo audio
              </Button>

              <Button variant="ghost" disabled={bulkSaving || generating} onClick={() => setSelected([])}>
                Bỏ chọn
              </Button>

              {selected.length > MAX_BATCH && (
                <span className="muted">Tạo audio tối đa {MAX_BATCH} chương mỗi lượt.</span>
              )}

              {/* Nói trước thay vì để máy chủ từ chối cả lô: giao dịch bên đó là
                  tất-cả-hoặc-không, nên một chương công khai lẫn vào sẽ làm hỏng
                  cả lệnh mà không đặt được giá cho chương nào. */}
              {bulkPriceBlocked && (
                <span className="muted">
                  Có chương công khai trong danh sách chọn — đổi mức khóa trước rồi mới đặt giá.
                </span>
              )}
            </div>
          )}

          <div className="admin-panel-body scroll-area">
            {chapters.length === 0 && (
              <EmptyState title="Truyện chưa có chương nào">
                Bấm “Thêm chương” để đăng chương đầu tiên.
              </EmptyState>
            )}

            {chapters.length > 0 && visibleChapters.length === 0 && (
              <EmptyState title="Không có chương nào khớp bộ lọc" />
            )}

            {visibleChapters.length > 0 && (
              <table className="nb-table">
                <thead>
                  <tr>
                    <th className="admin-cell-check">
                      <input
                        type="checkbox"
                        aria-label="Chọn tất cả chương đang hiện"
                        checked={allVisibleSelected}
                        onChange={toggleAllVisible}
                      />
                    </th>
                    <th>#</th>
                    <th>Tiêu đề</th>
                    <th>Mức khóa</th>
                    <th>Giá Xu</th>
                    <th>Audio</th>
                    <th className="admin-cell-actions">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleChapters.flatMap((chapter) => {
                    const audio = audioStatus[chapter.id];
                    const hasAudio = audio?.hasAudio ?? chapter.hasAudio;
                    const rows = [];

                    rows.push(
                      <tr
                        key={chapter.id}
                        className={selected.includes(chapter.id) ? "admin-row-editing" : ""}
                      >
                        <td className="admin-cell-check">
                          <input
                            type="checkbox"
                            aria-label={`Chọn chương ${chapter.chapterNumber}`}
                            checked={selected.includes(chapter.id)}
                            onChange={() => toggleSelected(chapter.id)}
                          />
                        </td>
                        <td>{chapter.chapterNumber}</td>
                        <td className="admin-cell-main">
                          {chapter.title}
                          {/* Chỉ nói khi có gì để nói: chương đã đăng là trạng
                              thái thường, và một cái nhãn trên mọi dòng chỉ làm
                              hai dòng đáng chú ý kia khó thấy hơn. */}
                          {chapter.publishState !== "PUBLISHED" && (
                            <Badge tone={chapter.publishState === "DRAFT" ? "neutral" : "info"}>
                              {chapter.publishState === "DRAFT"
                                ? "Nháp"
                                : `Hẹn ${formatDateTime(chapter.publishedAt)}`}
                            </Badge>
                          )}
                        </td>
                        <td>
                          {/* The select both states the current level and
                              changes it, so no badge repeats it beside. */}
                          <Select
                            aria-label={`Mức khóa của chương ${chapter.chapterNumber}`}
                            style={{ width: "auto", padding: "0.25rem 0.4rem" }}
                            value={chapter.accessLevel}
                            disabled={savingId === chapter.id}
                            onChange={(event) =>
                              handleAccessLevelChange(chapter, event.target.value)
                            }
                          >
                            {ACCESS_LEVELS.map((level) => (
                              <option key={level.value} value={level.value}>
                                {level.label}
                              </option>
                            ))}
                          </Select>
                        </td>
                        <td>
                          <PriceCell
                            chapter={chapter}
                            disabled={savingId === chapter.id}
                            onCommit={(price) => handlePriceChange(chapter, price)}
                          />
                        </td>
                        <td>
                          {audio?.processing ? (
                            <span className="admin-audio-working">
                              <span className="spinner" aria-hidden="true" />
                              <Badge tone="info">Đang tạo…</Badge>
                            </span>
                          ) : hasAudio ? (
                            <Badge tone="public">Đã có</Badge>
                          ) : (
                            <Badge tone="neutral">Chưa có</Badge>
                          )}
                          {audio?.failed && !hasAudio && <Badge tone="danger">Lần trước hỏng</Badge>}
                        </td>
                        <td className="admin-cell-actions">
                          <div className="admin-row-actions">
                            {audio?.audioId && (
                              <Button
                                size="sm"
                                variant="info"
                                aria-expanded={previewing === chapter.id}
                                onClick={() =>
                                  setPreviewing((current) =>
                                    current === chapter.id ? null : chapter.id,
                                  )
                                }
                              >
                                {previewing === chapter.id ? "Đóng" : "Nghe thử"}
                              </Button>
                            )}
                            <AudioUploadButton
                              chapterId={chapter.id}
                              hasAudio={hasAudio}
                              onUploaded={() => {
                                notify(`Đã tải lên audio cho “${chapter.title}”.`);
                                load();
                              }}
                              onError={setError}
                            />
                            <ButtonLink to={`/admin/chuong/${chapter.id}`} size="sm">
                              Sửa
                            </ButtonLink>
                            <Button
                              size="sm"
                              variant="danger"
                              onClick={() => setPendingDelete(chapter)}
                            >
                              Xóa
                            </Button>
                          </div>
                        </td>
                      </tr>,
                    );

                    // The player gets a row of its own rather than a cell: it is
                    // wider than any column here, and squeezing it into one would
                    // set the whole table's column widths by it.
                    if (previewing === chapter.id && audio?.audioId) {
                      rows.push(
                        <tr key={`${chapter.id}-preview`} className="admin-row-preview">
                          <td colSpan={7}>
                            <AudioPreview
                              streamUrl={`/api/chapters/${chapter.id}/audio/${audio.audioId}`}
                            />
                          </td>
                        </tr>,
                      );
                    }

                    return rows;
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div className="admin-panel-foot">
            <span className="muted" style={{ fontSize: "0.85rem", fontWeight: 500 }}>
              {/* Nói rõ hai con số là hai phạm vi khác nhau: bộ lọc chạy trên
                  trang đang mở, còn tổng số là của cả truyện. Không nói ra thì
                  "12/200" trên một truyện 1.200 chương là một con số sai. */}
              Hiển thị {visibleChapters.length}/{chapters.length} chương trong trang
              {page && page.totalElements > chapters.length
                ? ` · tổng ${page.totalElements} chương`
                : ""}{" "}
              · {audioCount} chương có audio
            </span>
          </div>

          {page && page.totalPages > 1 && (
            <div className="admin-panel-foot">
              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                onChange={(next) => {
                  // Bỏ phần đang chọn khi sang trang: những dòng ấy không còn
                  // trên màn hình, mà mọi thao tác hàng loạt ở đây đều nói về
                  // "những dòng đang thấy".
                  setSelected([]);
                  load(next);
                }}
              />
            </div>
          )}
        </section>
      </div>

      <ConfirmDialog
        open={Boolean(pendingDelete)}
        title="Xóa chương?"
        message={`Chương “${pendingDelete?.title}” sẽ bị xóa khỏi truyện.`}
        detail="File audio của chương cũng bị xóa theo, không khôi phục được."
        confirmLabel="Xóa chương"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </AdminPage>
  );
}
