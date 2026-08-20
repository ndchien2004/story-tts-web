import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { audioApi, chapterApi, progressApi } from "../api/endpoints";
import useAudioMixer from "../audio/useAudioMixer";
import useBgm from "../audio/useBgm";
import useTranscript from "../audio/useTranscript";
import useKaraoke from "../audio/karaoke/useKaraoke";
import AudioPlayer from "../components/AudioPlayer";
import ContentRemovedNotice, {
  isContentGone,
  UNKNOWN_DELETION,
} from "../components/ContentRemovedNotice";
import KaraokeText from "../components/KaraokeText";
import LockedGate from "../components/LockedGate";
import PurchaseReceipt from "../components/PurchaseReceipt";
import ReaderSettings from "../components/ReaderSettings";
import StoryAssistant from "../components/StoryAssistant";
import ThemeToggle from "../components/ThemeToggle";
import { useAuth } from "../context/auth-context";
import useChapterAudio from "../hooks/useChapterAudio";
import useChapterUpdates from "../hooks/useChapterUpdates";
import useTtsStatus from "../hooks/useTtsStatus";
import { Alert, Button, ButtonLink, ChevronIcon, Spinner } from "../components/ui";

const AUTO_CONTINUE_KEY = "storytts.autoContinue";
const KARAOKE_KEY = "storytts.karaoke.v1";

/** How close to the bottom of the text still counts as having reached the end. */
const END_THRESHOLD_PX = 40;

/** How often the listening position is written back while audio plays. */
const POSITION_SAVE_INTERVAL_MS = 15_000;

/** Bám chữ và tự cuộn đều bật sẵn: đó là lý do người ta mở một trang truyện audio. */
function readKaraokePreferences() {
  try {
    const raw = localStorage.getItem(KARAOKE_KEY);
    if (!raw) return { enabled: true, autoScroll: true };

    const parsed = JSON.parse(raw);
    return {
      enabled: typeof parsed?.enabled === "boolean" ? parsed.enabled : true,
      autoScroll: typeof parsed?.autoScroll === "boolean" ? parsed.autoScroll : true,
    };
  } catch {
    return { enabled: true, autoScroll: true };
  }
}

/**
 * Reading screen.
 *
 * Ba dải chồng lên nhau: thanh chuyển chương ở trên, trang chữ ở giữa và tự
 * cuộn lấy, phần nghe là một dải thu gọn dưới đáy. Nhờ trang chữ cuộn trong
 * lòng nó chứ không cuộn cả trang, hai dải kia đứng yên tại chỗ dù chương dài
 * tới đâu — bấm "chương sau" hay bấm phát đều không phải cuộn đi tìm.
 *
 * <h3>Ai giữ tiếng</h3>
 * Không phải khối này, và cũng không phải trình phát. Tiếng do bộ trộn giữ —
 * một `AudioContext` duy nhất dựng ở đây và sống suốt buổi đọc, kể cả khi người
 * đọc sang chương khác. Nhờ vậy nhạc nền không đứt quãng ở ranh giới hai chương,
 * và phần tô sáng chữ đọc đồng hồ từ đúng cái nguồn đang phát ra tiếng chứ
 * không từ một bản sao trong trạng thái React.
 */
export default function ChapterPage() {
  const { chapterId } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // One "đã đọc" call per chapter: reaching the end, clicking on and letting the
  // narration run out can all fire, and only the first needs to reach the server.
  const markedRead = useRef(false);
  const contentRef = useRef(null);
  const textRef = useRef(null);
  const lastPositionSaveRef = useRef(0);

  const [chapter, setChapter] = useState(null);
  const [lockError, setLockError] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  // Chương đã bị gỡ *trước khi* người đọc tới. Tách khỏi `error` vì nó không
  // phải một sự cố: nó là một câu trả lời dứt khoát, và nó có màn hình riêng
  // thay cho dải đỏ vô nghĩa "Không tìm thấy chương với id = 41".
  const [removedOnArrival, setRemovedOnArrival] = useState(false);

  // Chương bán lẻ bằng Xu: giá, số dư và phần còn thiếu, lấy từ chính lần bị từ
  // chối. Null nghĩa là chương này không nằm sau một cái giá nào.
  const [purchase, setPurchase] = useState(null);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseError, setPurchaseError] = useState(null);

  // Biên nhận sau khi vừa trả Xu. Sống độc lập với việc tải chương, vì nó phải ở
  // lại trên màn hình trong lúc chương được tải sẵn phía sau.
  const [receipt, setReceipt] = useState(null);

  // Tăng lên sau khi mở khóa xong, để hiệu ứng tải chương chạy lại. Tải lại
  // nguyên vẹn thay vì tự ghép nội dung vào trạng thái: máy chủ là bên quyết
  // định ai đọc được gì, và một bản ghép ở trình duyệt là một bản sao của quyết
  // định ấy có thể sai.
  const [reloadKey, setReloadKey] = useState(0);
  const [autoContinue, setAutoContinue] = useState(
    () => localStorage.getItem(AUTO_CONTINUE_KEY) === "true",
  );
  const [karaokePreferences, setKaraokePreferences] = useState(readKaraokePreferences);

  // Set only when the previous chapter finished playing; the mixer reads it to
  // decide whether it may start on its own, then it is cleared.
  const [autoPlayNext, setAutoPlayNext] = useState(false);

  // Audio is only fetched once the chapter itself proved readable, so a locked
  // chapter never triggers a second request that is bound to be refused.
  //
  // The version travels with it: a track narrating the previous wording is not
  // this chapter's audio, and the hook needs to know which wording is on screen
  // to tell the difference.
  const audio = useChapterAudio(chapterId, {
    enabled: Boolean(chapter),
    contentVersion: chapter?.contentVersion,
  });

  // Tells us the moment an admin rewrites — or removes — the chapter under the
  // reader. Two events on one stream; see the hook for why they stay apart.
  const { staleVersion, dismiss: dismissUpdate, deletion } = useChapterUpdates(
    chapter ? chapterId : null,
    chapter?.contentVersion,
  );

  /*
   * Bản sao của `deletion` để những hàm gọi lại đọc, thay vì phụ thuộc vào nó.
   *
   * Đưa `deletion` thẳng vào danh sách phụ thuộc của `savePosition` thì hàm ấy
   * đổi danh tính đúng lúc nội dung bị gỡ — và hiệu ứng "ghi lại chỗ nghe dở khi
   * rời trang" nhận `savePosition` làm phụ thuộc, nên nó chạy phần dọn dẹp của
   * mình ngay tại đó. Phần dọn dẹp ấy ghi tiến độ, bằng bản cũ của hàm, tức là
   * bản chưa biết chương đã bị gỡ: đúng một request cho một chương không còn tồn
   * tại, ngay tại thời điểm mà tất cả những dòng này tồn tại để ngăn nó.
   *
   * Một ref không đổi danh tính, nên chuỗi ấy không bao giờ bắt đầu.
   */
  const deletionRef = useRef(null);
  useEffect(() => {
    deletionRef.current = deletion;
  }, [deletion]);

  // Refreshed after each generation, since producing one spends a daily go.
  const { status: ttsStatus, refresh: refreshTtsStatus } = useTtsStatus();

  // One automatic narration per chapter. Without this the effect below would
  // fire again on every state change and spend the reader's whole allowance on
  // a single chapter.
  const autoNarrationTried = useRef(false);

  // Set on the way out of a finishing chapter and read on the way into the next
  // one. `autoPlayNext` cannot answer "did I arrive by autoplay?" on its own,
  // because pressing the narration button sets it too — and reading it that way
  // would turn a failed press into an automatic retry that costs another go.
  const arrivingByAutoContinue = useRef(false);
  const arrivedByAutoContinue = useRef(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setChapter(null);
    setLockError(null);
    setError(null);
    setRemovedOnArrival(false);
    setPurchase(null);
    setPurchaseError(null);
    // Nút mở khóa cố ý không tự tắt trạng thái chờ khi thành công: nó sống tới
    // lúc chương tải xong. Đặt lại ở đây để nếu lần tải ấy vẫn bị từ chối thì
    // màn hình mở khóa hiện ra với một cái nút bấm được, không phải nút quay mãi.
    setPurchasing(false);

    chapterApi
      .detail(chapterId)
      .then((data) => {
        if (!cancelled) setChapter(data);
      })
      .catch((err) => {
        if (cancelled) return;
        // A locked chapter is an expected outcome, not a failure to report.
        if (err.isLocked) {
          setLockError(err);
        } else if (err.isPurchaseRequired) {
          // Chương bán lẻ: cũng là kết quả bình thường, và là kết quả duy nhất
          // người đọc xử lý được ngay tại chỗ.
          setPurchase({
            coinPrice: Number(err.details?.coinPrice ?? 0),
            balance: Number(err.details?.balance ?? 0),
            shortfall: Math.max(
              Number(err.details?.coinPrice ?? 0) - Number(err.details?.balance ?? 0),
              0,
            ),
          });
        } else if (isContentGone(err)) {
          // Chương đã bị gỡ trước khi người này mở link — từ một trang cũ, một
          // dấu trang, hay một chương vừa biến mất trong lúc họ bấm. Cùng một
          // sự thật với lời báo tức thời, nên cùng một màn hình.
          setRemovedOnArrival(true);
        } else {
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [chapterId, reloadKey]);

  /**
   * Người đọc chọn chuyển sang nội dung mới.
   *
   * <p>Chỉ chạy khi họ bấm, và đó là toàn bộ điểm của nó. Tự tải lại giữa lúc
   * người ta đang đọc dở là cướp mất đoạn văn dưới mắt họ; tự đổi audio giữa
   * chừng là cắt ngang câu đang nghe và mất luôn vị trí. Cái giá của việc chờ
   * là trong lúc ấy họ đọc bản cũ — nhưng họ biết là bản cũ, vì có một dòng
   * ngay trên đầu nói thế.
   *
   * <p>Bấm rồi thì mọi thứ đi theo một cách tự nhiên chứ không phải nhờ dọn dẹp
   * bằng tay: chương tải lại mang theo phiên bản mới, {@code useChapterAudio}
   * thấy phiên bản đổi nên bỏ danh sách bản audio cũ và hỏi lại từ đầu, còn
   * bản nào của phiên bản cũ thì máy chủ không trả về nữa.
   */
  const handleLoadNewVersion = useCallback(() => {
    dismissUpdate();
    setReloadKey((key) => key + 1);
  }, [dismissUpdate]);

  /**
   * Trả Xu để mở chương, rồi tải lại chương như một lần mở bình thường.
   *
   * <p>Gọi trùng không tốn thêm Xu — máy chủ trả về ALREADY_OWNED thay vì trừ
   * lần nữa — nên bấm hai lần hay trình duyệt tự gửi lại đều vô hại. Nút vẫn có
   * trạng thái chờ, nhưng để người bấm biết máy đang làm việc, không phải để
   * chặn lần bấm thứ hai.
   */
  const handlePurchase = useCallback(async () => {
    setPurchasing(true);
    setPurchaseError(null);
    try {
      const result = await chapterApi.purchase(chapterId);

      /*
       * Chỉ dựng biên nhận khi thật sự vừa trả tiền.
       *
       * Máy chủ trả về ALREADY_OWNED khi request bị gửi lại, và ALREADY_ACCESSIBLE
       * khi người bấm vốn đã đọc được (VIP). Cả hai đều là thành công, nhưng
       * không có đồng nào rời khỏi ví — dựng một tờ biên nhận "đã trừ 0 Xu" cho
       * chúng là nói dối về một giao dịch chưa từng xảy ra.
       */
      if (result.outcome === "PURCHASED") {
        setReceipt({ coinsSpent: result.coinsSpent, balance: result.balance });
      }

      // Không tự ghép nội dung vào trạng thái: hỏi lại máy chủ, vì đó mới là
      // bên quyết định ai đọc được gì. Chạy ngay chứ không đợi người đọc bấm,
      // để lúc họ bấm thì chương đã sẵn sàng.
      setPurchase(null);
      setReloadKey((value) => value + 1);
    } catch (err) {
      // Hết Xu giữa chừng (một tab khác vừa tiêu mất) trả về đúng giá và số dư
      // mới, nên màn hình cập nhật lại thay vì chỉ báo lỗi.
      if (err.isPurchaseRequired && err.details) {
        const coinPrice = Number(err.details.coinPrice ?? 0);
        const balance = Number(err.details.balance ?? 0);
        setPurchase({ coinPrice, balance, shortfall: Math.max(coinPrice - balance, 0) });
      }
      setPurchaseError(err.message);
      setPurchasing(false);
    }
  }, [chapterId]);

  /*
   * Biên nhận thuộc về đúng một lần mua, nên nó biến mất khi sang chương khác.
   *
   * Hiệu ứng riêng theo `chapterId` chứ không dọn chung với phần tải chương:
   * phần ấy còn chạy lại theo `reloadKey`, mà chính lần chạy lại ấy là lần biên
   * nhận cần ở lại trên màn hình.
   */
  useEffect(() => {
    setReceipt(null);
  }, [chapterId]);

  useEffect(() => {
    localStorage.setItem(AUTO_CONTINUE_KEY, String(autoContinue));
  }, [autoContinue]);

  useEffect(() => {
    localStorage.setItem(KARAOKE_KEY, JSON.stringify(karaokePreferences));
  }, [karaokePreferences]);

  /**
   * Open the chapter's progress record, so "Đọc tiếp" on the story page knows
   * where the reader stopped even if they never reach the end.
   */
  useEffect(() => {
    markedRead.current = false;
    if (!chapter || !isAuthenticated) return;

    // Progress is a convenience, never something worth interrupting reading for.
    progressApi.save(chapterId, { lastPosition: 0 }).catch(() => {});
  }, [chapter, chapterId, isAuthenticated]);

  /**
   * Writes the listening position back so the track resumes next time (4.4).
   *
   * The database keeps one position per chapter, not per track, which is enough:
   * a chapter has one narration worth following, and a reader who switches
   * between a recording and a generated version wants the same place in the text
   * either way.
   */
  const savePosition = useCallback(
    (seconds) => {
      if (!isAuthenticated) return;
      // Chương đã bị gỡ thì không còn chỗ nào để ghi tiến độ vào. Lời gọi ấy
      // vốn nuốt lỗi nên nó vô hại, nhưng nó vẫn là một request gửi đi cho một
      // thứ không tồn tại — và nó sẽ bắn đúng vào lúc trang đang dừng lại, kể cả
      // từ chính lệnh tạm dừng bên dưới (bộ trộn báo `onPause` về đây).
      //
      // Đọc qua ref chứ không qua phụ thuộc — xem ghi chú ở `deletionRef`.
      if (deletionRef.current) return;
      progressApi
        .save(chapterId, { audioPositionSeconds: Math.floor(seconds) })
        .catch(() => {});
    },
    [chapterId, isAuthenticated],
  );

  /**
   * Throttles the write. The mixer reports the position several times a second,
   * and it only needs to be roughly right — losing the last few seconds of a
   * session matters far less than a request per frame.
   */
  const handlePositionChange = useCallback(
    (seconds) => {
      const now = Date.now();
      if (now - lastPositionSaveRef.current < POSITION_SAVE_INTERVAL_MS) return;
      lastPositionSaveRef.current = now;
      savePosition(seconds);
    },
    [savePosition],
  );

  /** Records the chapter as finished; safe to call more than once. */
  const markRead = useCallback(() => {
    if (markedRead.current || !isAuthenticated) return;
    markedRead.current = true;
    progressApi.markRead(chapterId).catch(() => {});
  }, [chapterId, isAuthenticated]);

  /**
   * Continuous listening: move to the next chapter when playback finishes.
   *
   * Only the navigation happens here. The next page loads its own audio and the
   * access check runs again server-side either way.
   */
  const handleTrackEnded = useCallback(() => {
    markRead();
    savePosition(0);
    if (autoContinue && chapter?.nextChapterId) {
      // The one case where audio may start by itself: the listener is already
      // listening, and asked for the next chapter to follow on.
      arrivingByAutoContinue.current = true;
      setAutoPlayNext(true);
      navigate(`/chuong/${chapter.nextChapterId}`);
    }
  }, [autoContinue, chapter, markRead, navigate, savePosition]);

  /*
   * Một bộ trộn cho cả buổi đọc.
   *
   * Dựng ở đây chứ không trong trình phát, vì nó phải sống lâu hơn bất kỳ khối
   * giao diện nào: đổi chương là đổi nguồn tiếng, không phải dựng lại đường
   * tiếng — nhạc nền đang chạy thì cứ chạy tiếp qua ranh giới hai chương.
   */
  const { engine, state: mixerState } = useAudioMixer({
    onEnded: handleTrackEnded,
    onTimeUpdate: handlePositionChange,
    onPause: savePosition,
  });

  const bgm = useBgm(engine);

  const activeTrack = audio.activeTrack;
  const streamUrl = useMemo(
    () => (activeTrack?.streamUrl ? audioApi.streamUrl(activeTrack.streamUrl) : null),
    [activeTrack],
  );

  /*
   * Nạp bản audio đang chọn vào bộ trộn.
   *
   * `autoPlayNext` nằm trong danh sách phụ thuộc chứ không chỉ được đọc một lần:
   * lời mời tự phát có thể tới sau lúc bản audio đã nạp xong — người đọc bấm
   * "Nghe bằng AI" thì bản mới được nhận vào và cờ tự phát được bật gần như cùng
   * lúc, và thứ tự giữa hai việc ấy không có gì bảo đảm.
   */
  // Nhớ cả bộ trộn chứ không chỉ đường dẫn: một bộ trộn khác là một đồ thị âm
  // thanh khác, và bản audio phải được nạp lại vào nó dù đường dẫn không đổi.
  const loaded = useRef({ engine: null, src: null });

  useEffect(() => {
    const alreadyLoaded = loaded.current.engine === engine && loaded.current.src === streamUrl;

    if (alreadyLoaded) {
      if (streamUrl && autoPlayNext) {
        setAutoPlayNext(false);
        void engine.play();
      }
      return;
    }

    loaded.current = { engine, src: streamUrl };
    void engine.loadNarration(streamUrl, {
      startAt: chapter?.audioPositionSeconds ?? 0,
      autoPlay: autoPlayNext,
    });
    if (autoPlayNext) setAutoPlayNext(false);
  }, [autoPlayNext, chapter, engine, streamUrl]);

  /**
   * Nội dung vừa bị gỡ dưới tay người đang nghe: dừng tiếng ngay.
   *
   * <p>Cái hộp chặn ở trên che mất mọi nút bấm, nhưng che không phải là dừng —
   * không có chỗ này thì giọng đọc của một chương đã bị gỡ vẫn tiếp tục vang
   * sau lưng lời báo rằng nó không còn nữa, và người nghe không còn cách nào
   * tắt nó. Đây là nửa "dừng" của yêu cầu; nửa "chặn" là cái hộp.
   *
   * <p>Dừng cả nhạc nền theo. Nhạc nền là của người nghe chứ không của chương,
   * nhưng nó được bật lên để đệm cho một buổi đọc vừa chấm dứt — để nó chạy
   * tiếp một mình dưới một hộp thoại báo tin xấu là hỏng cả hai.
   *
   * <p>Tạm dừng chứ không tháo bỏ đường tiếng: bộ trộn sống lâu hơn màn hình
   * này và sẽ phục vụ chương tiếp theo người ta mở.
   */
  useEffect(() => {
    if (!deletion) return;
    engine.pause();
    engine.pauseBgm();
  }, [deletion, engine]);

  /**
   * Giữ lại chỗ nghe dở khi rời trang.
   *
   * Đóng thẳng tab hay bấm quay lại đều không sinh ra sự kiện tạm dừng nào, nên
   * không có chỗ này thì đoạn từ lần ghi định kỳ gần nhất tới lúc rời đi bị mất
   * — mà đó đúng là đoạn người vừa rời đi quan tâm.
   */
  useEffect(() => () => {
    const seconds = engine.exactPosition();
    if (seconds > 0) savePosition(seconds);
  }, [engine, savePosition]);

  /* -------------------------------------------------------------- */
  /* Bám chữ theo giọng đọc                                          */
  /* -------------------------------------------------------------- */

  const karaokeAvailable = Boolean(activeTrack?.hasTranscript);

  const transcript = useTranscript(
    chapterId,
    karaokeAvailable ? activeTrack.id : null,
    chapter?.content,
    karaokePreferences.enabled,
  );

  const { following, resumeFollowing } = useKaraoke({
    engine,
    timeline: transcript.timeline,
    containerRef: textRef,
    enabled: karaokePreferences.enabled && Boolean(transcript.timeline),
    autoScroll: karaokePreferences.autoScroll,
  });

  /** Bấm vào một chữ là nói "đọc lại từ đây" — một cách tua bằng mắt. */
  const seekToWord = useCallback(
    (wordIndex) => {
      const at = transcript.timeline?.wordStart(wordIndex);
      if (at !== null && at !== undefined) engine.seek(at);
    },
    [engine, transcript.timeline],
  );

  /**
   * Reaching the bottom of the text counts as having read the chapter — that
   * also covers the last chapter of a story, where there is no "next" to click.
   */
  const handleScroll = useCallback(
    (event) => {
      const { scrollTop, clientHeight, scrollHeight } = event.currentTarget;
      if (scrollTop + clientHeight >= scrollHeight - END_THRESHOLD_PX) markRead();
    },
    [markRead],
  );

  /**
   * A chapter short enough to fit the pane never fires a scroll event, so it
   * would never reach the check above. Nothing is left to read in that case,
   * so opening it is finishing it.
   */
  useEffect(() => {
    const pane = contentRef.current;
    if (!chapter || !pane) return;
    if (pane.scrollHeight <= pane.clientHeight + END_THRESHOLD_PX) markRead();
  }, [chapter, markRead]);

  function goToChapter(id) {
    markRead();
    navigate(`/chuong/${id}`);
  }

  /**
   * Continuous listening across a chapter nobody has narrated yet.
   *
   * Arriving here by autoplay means someone is listening with their hands full,
   * so a chapter without a track would end the session in silence. Producing one
   * is the only way to carry on — and it is bounded: the attempt is made once per
   * chapter, only for a listener who arrived automatically, and only while goes
   * remain. Opening a chapter by hand never triggers it, or browsing chapters
   * would quietly drain the day's allowance.
   */
  useEffect(() => {
    autoNarrationTried.current = false;
    arrivedByAutoContinue.current = arrivingByAutoContinue.current;
    arrivingByAutoContinue.current = false;
  }, [chapterId]);

  useEffect(() => {
    if (!arrivedByAutoContinue.current || !autoContinue) return;
    if (audio.loading || audio.hasAudio || audio.generating) return;
    if (autoNarrationTried.current) return;
    if (!isAuthenticated || !ttsStatus?.enabled) return;
    // Out of goes, or the chapter is too long to narrate: the chain stops here
    // rather than asking for something the server will refuse.
    if (ttsStatus.remainingToday === 0) return;
    if (ttsStatus.maxChars > 0 && (chapter?.content?.length ?? 0) > ttsStatus.maxChars) return;

    autoNarrationTried.current = true;
    audio.requestTts().then(() => refreshTtsStatus());
  }, [audio, autoContinue, chapter, isAuthenticated, refreshTtsStatus, ttsStatus]);

  /** A track the reader asked for may start on its own; that press was the consent. */
  useEffect(() => {
    if (audio.playWhenReady) {
      setAutoPlayNext(true);
      audio.clearPlayWhenReady();
      refreshTtsStatus();
    }
  }, [audio, refreshTtsStatus]);

  /*
   * Biên nhận đứng trước cả nhánh "đang tải".
   *
   * Chương được tải lại ngay sau khi trả tiền, nên nếu để sau thì cái người đọc
   * thấy đầu tiên sau cú bấm là một vòng quay — rồi thẳng vào chương, đúng cái
   * im lặng vừa phải sửa. Đứng trước thì biên nhận hiện ra ngay, và việc tải
   * diễn ra phía sau nó.
   */
  /*
   * `!purchase && !error` không phải phòng xa suông: nếu lần tải lại sau khi trả
   * tiền lại bị từ chối hoặc hỏng mạng, chương sẽ không bao giờ về, và một biên
   * nhận có cái nút quay mãi là thứ tệ hơn hẳn không có biên nhận. Hai điều kiện
   * ấy nhường màn hình lại cho nhánh biết cách nói chuyện gì đang xảy ra.
   */
  if (receipt && !purchase && !error) {
    return (
      <div className="container-narrow page">
        <PurchaseReceipt
          coinsSpent={receipt.coinsSpent}
          balance={receipt.balance}
          loading={loading || !chapter}
          onContinue={() => setReceipt(null)}
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="page">
        <Spinner label="Đang tải chương…" />
      </div>
    );
  }

  if (purchase) {
    return (
      <div className="container-narrow page">
        <LockedGate
          purchase={purchase}
          onPurchase={handlePurchase}
          purchasing={purchasing}
          purchaseError={purchaseError}
        />
      </div>
    );
  }

  if (lockError) {
    return (
      <div className="container-narrow page">
        <LockedGate
          requiredAccessLevel={lockError.requiredAccessLevel}
          message={lockError.message}
        />
      </div>
    );
  }

  /*
    Chương đã bị gỡ trước khi người này tới.

    Đứng trước nhánh `error` chứ không lẫn vào nó: đây không phải một sự cố mà
    là một câu trả lời dứt khoát, và trước đây nó hiện ra dưới dạng một dải đỏ
    mang nguyên văn "Không tìm thấy chương với id = 41" — đúng, nhưng đọc như
    một lỗi do người dùng gây ra, có một số id của cơ sở dữ liệu trong đó, và
    không chỉ ra chỗ nào để đi tiếp.
  */
  if (removedOnArrival) {
    return (
      <div className="container-narrow page">
        <ContentRemovedNotice deletion={UNKNOWN_DELETION} />
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <Alert tone="error">{error}</Alert>
      </div>
    );
  }

  if (!chapter) return null;

  return (
    <div className="reader">
      {/*
        Thanh trên duy nhất của màn hình đọc — nó vừa là thanh chuyển chương,
        vừa thay chỗ cho thanh điều hướng chung của cả trang web, cái tự ẩn đi
        khi màn hình này hiện ra (xem `body:has(.reader)` trong components.css).

        Đọc truyện là một chế độ chứ không phải một trang. Việc của thanh điều
        hướng chung là "đi chỗ khác trong web", mà đang đọc dở chương bốn mươi
        bảy thì "chỗ khác" chỉ có ba nghĩa: về danh sách chương, chương trước,
        chương sau — cả ba đều nằm ngay đây. Cái logo và mấy link thể loại suốt
        buổi đọc không được bấm lần nào, trong khi chúng ăn mất một dải cao bốn
        phân trên đầu mỗi trang chữ.

        Danh tính nằm bên trái, chỗ mắt bắt đầu; điều khiển dồn sang phải.
      */}
      <header className="reader-bar">
        {/*
          Đường quay lại, và nó phải là một cái nút nhìn ra được.

          Đường ấy vốn đã có: tên truyện ngay bên cạnh là một `Link` về đúng chỗ
          này. Nhưng nó được tạo hình như một dòng chú thích — chữ nhỏ, in hoa,
          màu nhạt, không gạch chân — nên mắt đọc nó là "đây là truyện gì" chứ
          không phải "bấm vào đây để về". Cái mũi tên này không thêm chức năng
          nào, nó chỉ làm chức năng sẵn có nhìn thấy được.

          Về thẳng trang truyện chứ không lùi một bước trong lịch sử: có "nghe
          liên tục", người đọc có thể đã trôi qua năm chương liền, và lùi một
          bước sẽ ném họ về chương vừa nghe xong chứ không về danh sách chương.
        */}
        <ButtonLink
          to={`/truyen/${chapter.storyId}`}
          className="nb-icon-btn reader-back"
          aria-label={`Quay lại danh sách chương của ${chapter.storyTitle}`}
          title="Quay lại danh sách chương"
        >
          <ChevronIcon />
        </ButtonLink>

        {/* The story it belongs to sits above the chapter's own name, so the
            bar answers "where am I" without a second header below it. */}
        <div className="reader-bar-title">
          <Link to={`/truyen/${chapter.storyId}`} className="reader-bar-story">
            {chapter.storyTitle}
          </Link>
          <h1 id="reader-chapter-title">{chapter.title}</h1>
        </div>

        <div className="reader-bar-nav">
          <Button
            className="reader-nav-btn"
            disabled={!chapter.previousChapterId}
            onClick={() => goToChapter(chapter.previousChapterId)}
          >
            <ChevronIcon />
            Chương trước
          </Button>

          <Button
            className="reader-nav-btn"
            variant="primary"
            disabled={!chapter.nextChapterId}
            onClick={() => goToChapter(chapter.nextChapterId)}
          >
            Chương sau
            <ChevronIcon right />
          </Button>
        </div>

        {/* Hai thứ đi theo từ thanh điều hướng chung và từ đầu trang chữ về đây.
            Cỡ chữ và sáng/tối đều là chuyện của riêng màn hình này, và cái thứ
            hai là thứ người đọc ban đêm tìm đầu tiên — mất nó cùng với navbar
            thì mất thật. */}
        <div className="reader-bar-tools">
          <ReaderSettings />
          <ThemeToggle />
        </div>
      </header>

      {/*
        Chương đã đổi dưới tay người đang đọc.

        Một dòng chữ và một cái nút, không hơn. Không tự tải lại, không dừng
        audio đang phát, không nhảy về đầu chương — cả ba đều là lấy quyền quyết
        định khỏi tay người đang dùng để đổi lấy một sự đúng đắn mà chính họ
        chưa yêu cầu. Họ nghe nốt câu đang nghe, đọc nốt đoạn đang đọc, rồi bấm
        khi nào thấy hợp.

        Đặt ngay dưới thanh trên chứ không nổi đè lên chữ: đây là tin cần biết,
        không phải tin phải xử lý ngay.
      */}
      {staleVersion !== null && (
        <div className="reader-update-notice" role="status">
          <span>Chương này vừa được cập nhật.</span>
          <Button variant="primary" onClick={handleLoadNewVersion}>
            Đọc nội dung mới
          </Button>
        </div>
      )}

      {/*
        Nội dung vừa bị gỡ dưới tay người đang đọc.

        Cùng một luồng SSE với dòng thông báo ngay trên, và cố ý trông khác hẳn
        nó. Dải trên là một lời mời: chương vẫn còn, có bản mới, đọc lúc nào tùy
        bạn. Cái này là một bức tường: không còn gì để đọc, nên màn hình dừng
        lại thay vì gợi ý. Đó cũng là chỗ nội dung đã trả tiền thôi không với
        tới được nữa.

        Trang chữ và thanh nghe vẫn nằm dưới lớp phủ chứ không bị tháo khỏi cây
        DOM. Tháo chúng đi giữa chừng sẽ hủy bộ trộn và mọi thứ móc vào nó ngay
        trong lúc React đang dựng cái hộp này — nhiều việc hơn, và mỗi việc là
        một chỗ hỏng mới, cho một thứ không ai nhìn thấy. Tiếng đã tắt bằng một
        hiệu ứng ở trên, và lớp phủ chặn mọi cú bấm.
      */}
      {deletion && <ContentRemovedNotice deletion={deletion} asModal />}

      <div className="reader-grid">
        {/* Tên chương trên thanh trên là tiêu đề của chính khối chữ này, nên nó
            đứng tên cho khối luôn. Chỗ này từng có một dòng "Nội dung" làm việc
            ấy, và đó là cả một dải ngang cao hơn ba phân để nói một điều mà bất
            cứ ai nhìn vào trang chữ cũng đã biết. */}
        <section className="reader-pane" aria-labelledby="reader-chapter-title">
          <div className="reader-pane-body scroll-area" ref={contentRef} onScroll={handleScroll}>
            <KaraokeText
              content={chapter.content ?? ""}
              timeline={transcript.timeline}
              innerRef={textRef}
              onSeekToWord={seekToWord}
            />

            {/* Người đọc vừa cuộn tay đi chỗ khác: phần tự cuộn đã nhường, và
                đây là đường quay lại — do họ bấm, chứ không phải bị kéo về. */}
            {transcript.timeline && karaokePreferences.autoScroll && !following && (
              <button type="button" className="karaoke-resume" onClick={resumeFollowing}>
                Về chỗ đang đọc
              </button>
            )}
          </div>
        </section>

        {/*
          Trợ lý AI, và nó nằm trong khối này chứ không cạnh AudioPlayer — đó
          là cả cách nó biết chỗ để đứng.

          Khối .reader-grid kết thúc đúng ở chỗ thanh nghe bắt đầu, nên một cái
          hộp neo vào đáy nó tự nằm sát trên thanh ấy, và tự dịch lên khi thanh
          ấy mở phần mở rộng ra. Đặt cạnh AudioPlayer thì phải đo chiều cao thanh
          nghe bằng JavaScript mới làm được đúng việc ấy. Xem assistant.css.

          Chỉ nhận chapterId: nội dung chương do máy chủ tự tra sau khi xét
          quyền, nên hộp này không bao giờ cầm trong tay thứ nó hỏi về.
        */}
        <StoryAssistant chapterId={chapterId} isAuthenticated={isAuthenticated} />
      </div>

      {/* Phần nghe không còn là một cột bên cạnh trang chữ mà là một dải dưới
          đáy màn hình: thu gọn thì chỉ còn cái tay với tới giữa chương, mở ra
          thì có đủ mọi tuỳ chọn. Xem AudioPlayer về chỗ nào chứa cái gì. */}
      <AudioPlayer
        audio={audio}
        ttsStatus={ttsStatus}
        isAuthenticated={isAuthenticated}
        chapterLength={chapter.content?.length ?? 0}
        storyTitle={chapter.storyTitle}
        chapterTitle={chapter.title}
        chapterId={chapterId}
        autoContinue={autoContinue}
        onToggleAutoContinue={() => setAutoContinue((value) => !value)}
        hasNextChapter={Boolean(chapter.nextChapterId)}
        engine={engine}
        mixerState={mixerState}
        bgm={bgm}
        karaoke={{
          available: karaokeAvailable,
          enabled: karaokePreferences.enabled,
          autoScroll: karaokePreferences.autoScroll,
          loading: transcript.loading,
          stale: transcript.stale,
          onToggle: (enabled) => setKaraokePreferences((current) => ({ ...current, enabled })),
          onToggleAutoScroll: (autoScroll) =>
            setKaraokePreferences((current) => ({ ...current, autoScroll })),
        }}
      />
    </div>
  );
}
