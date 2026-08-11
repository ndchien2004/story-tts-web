import { audioApi } from "../../api/endpoints";

/**
 * A plain audio element for checking a track in the console.
 *
 * Deliberately the browser's own controls rather than the reader's transport:
 * the question here is only "did this come out right", and the native player
 * answers it without the scrubber, the rate control and the chapter-advance
 * behaviour that belong to someone actually listening to a story.
 *
 * @param streamUrl relative path from the server; the token is attached here
 */
export default function AudioPreview({ streamUrl }) {
  if (!streamUrl) return null;

  return (
    <audio className="admin-audio-preview" controls preload="none" src={audioApi.streamUrl(streamUrl)}>
      Trình duyệt của bạn không phát được audio.
    </audio>
  );
}
