import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import Modal from "./Modal";
import { ButtonLink } from "./ui";

/**
 * Shown when the chapter a reader is on has been removed from the site.
 *
 * <h3>Why this is a wall and the "content was updated" notice is a line of text</h3>
 * Both arrive on the same SSE stream and both are announced the moment an
 * admin's transaction commits — see `useChapterUpdates`. They are deliberately
 * given opposite weight in the interface, because they ask opposite things:
 *
 * - **Rewritten**: the chapter is still there, and a newer version exists. The
 *   reader is offered it and otherwise left alone — no interruption, no stopped
 *   audio, no jump to the top. Taking over the screen for that would be taking
 *   a decision out of their hands that they never asked us to take.
 * - **Removed**: there is no chapter. Continuing is not a choice they still
 *   have, so the screen stops rather than suggests. This is also the point at
 *   which paid content has to stop being reachable, which is the other half of
 *   why it blocks.
 *
 * <h3>Two ways in, one set of words</h3>
 * A reader meets this either mid-sentence (the stream said so) or on arrival
 * (the chapter was already gone before the page loaded, so the API answered
 * 404). The wording and the way out have to be identical in both, or the site
 * explains the same fact two different ways depending on timing — hence one
 * component with a `asModal` switch rather than two screens that drift apart.
 *
 * <p>It replaces what used to happen on the second path: a bare red error alert
 * carrying the server's `Không tìm thấy chương với id = 41`. True, and useless
 * — it reads like a fault the reader caused, names a database id, and offers
 * nowhere to go.
 *
 * <h3>About the refund line</h3>
 * Conditional on purpose. The event stream needs no login — it carries nothing
 * private — so the server genuinely does not know whether *this* reader was one
 * of the people refunded; it only knows that someone was. Saying "your Xu has
 * been returned" to someone who never bought the chapter would be a confident
 * lie, so the sentence is phrased as the condition it actually is, and the real
 * number stays where it can be trusted: the wallet history.
 *
 * @param deletion `{ wholeStory, storyId, refunded }` from `useChapterUpdates`,
 *                 or a shape built by the page for the 404-on-arrival path
 * @param asModal  true to block a reader already on the page; false to render
 *                 as the page itself
 */
export default function ContentRemovedNotice({ deletion, asModal = false }) {
  const navigate = useNavigate();

  const wholeStory = Boolean(deletion?.wholeStory);
  const storyId = deletion?.storyId ?? null;
  const refunded = Boolean(deletion?.refunded);

  // Where "away from here" goes. The chapter list still exists when only the
  // chapter was removed; when the story went too, the only honest destination
  // left is the library.
  const backTo = !wholeStory && storyId ? `/truyen/${storyId}` : "/truyen";
  const backLabel = !wholeStory && storyId ? "Về danh sách chương" : "Xem truyện khác";

  const leave = useCallback(() => navigate(backTo), [navigate, backTo]);

  const title = wholeStory ? "Truyện đã bị gỡ" : "Chương đã bị gỡ";
  const body = wholeStory
    ? "Truyện này vừa được quản trị viên gỡ khỏi thư viện, nên chương bạn đang đọc không còn nữa."
    : "Chương này vừa được quản trị viên gỡ khỏi trang. Những chương còn lại của truyện vẫn đọc được bình thường.";

  const actions = (
    <>
      <ButtonLink to={backTo} variant="primary">
        {backLabel}
      </ButtonLink>
      <ButtonLink to="/">Về trang chủ</ButtonLink>
    </>
  );

  const message = (
    <>
      <p className="muted" style={{ maxWidth: "46ch" }}>
        {body}
      </p>

      {refunded && (
        <p className="muted" style={{ maxWidth: "46ch" }}>
          Nếu bạn đã dùng Xu để mở nội dung này, số Xu ấy đã được hoàn lại vào ví. Bạn có thể đối
          chiếu ở mục <b>Lịch sử Xu</b> trong trang tài khoản.
        </p>
      )}
    </>
  );

  if (!asModal) {
    return (
      <div className="locked-gate">
        <span className="locked-gate-tag">Nội dung không còn</span>
        <h2>{title}</h2>
        {message}
        <div className="row" style={{ gap: "var(--space-3)", flexWrap: "wrap" }}>{actions}</div>
      </div>
    );
  }

  return (
    <Modal
      open
      title={title}
      // `alertdialog`, not `dialog`: this interrupts to report something that
      // already happened rather than to ask for input, and screen readers
      // announce the two differently.
      role="alertdialog"
      // Escape, the backdrop and the corner button all lead out rather than
      // dismissing. There is nothing behind this box to go back to — the reader
      // it was covering is showing a chapter that no longer exists — so
      // "close" and "leave" are the same action, and pretending otherwise
      // would drop someone onto a dead page.
      onClose={leave}
      footer={<div className="row" style={{ gap: "var(--space-3)", flexWrap: "wrap" }}>{actions}</div>}
    >
      <div className="content-removed">
        <span className="locked-gate-tag">Nội dung không còn</span>
        {message}
      </div>
    </Modal>
  );
}

/**
 * Does this failure to load a chapter mean the chapter is gone?
 *
 * <p>Kept beside the notice rather than in the page, so the two questions the
 * feature asks — "is it gone" and "what do we say about it" — are answered in
 * one file. `NOT_FOUND` is the code `GlobalExceptionHandler` gives
 * `ResourceNotFoundException`, which is what the chapter endpoint throws for an
 * id that is no longer in the database.
 *
 * <p>Only 404 counts. A 500 or a dropped connection is a fault to report as
 * one; announcing "this chapter was removed" for a server that merely hiccupped
 * would send readers away from content that is still there.
 */
export function isContentGone(error) {
  return error?.status === 404 || error?.code === "NOT_FOUND";
}

/**
 * What the page passes on the 404-on-arrival path.
 *
 * <p>Everything is unknown there and the placeholder says so honestly. The
 * request that would have told us which story the chapter belonged to is the
 * one that just answered 404, and `refunded` stays false because a refund — if
 * there was one — happened before this reader ever arrived, and they can see it
 * in their wallet without being told here.
 */
export const UNKNOWN_DELETION = { wholeStory: false, storyId: null, refunded: false };
