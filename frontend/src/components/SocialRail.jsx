/**
 * Contact rail down the left edge of the home page.
 *
 * Two links and nothing else. They are drawn rather than imported: the page
 * has no icon dependency, and two glyphs do not justify adding one.
 *
 * Each entry carries its own brand colour on hover — the one place in the app
 * that breaks the monochrome palette, because a Facebook link that is not blue
 * has to be read before it is recognised.
 */

const FACEBOOK_URL = "https://www.facebook.com/ndchien12/";
const ZALO_PHONE = "0775007068";

/** Lowercase f in a rounded square — the mark as a letterform, not artwork. */
const FacebookGlyph = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path
      d="M13.6 21.5v-8.1h2.7l.4-3.2h-3.1V8.2c0-.9.3-1.5 1.6-1.5h1.6V3.8c-.3 0-1.3-.1-2.4-.1-2.4 0-4 1.4-4 4.1v2.4H7.7v3.2h2.7v8.1z"
    />
  </svg>
);

/** Speech bubble: Zalo is a messaging app, and a bubble says so without the wordmark. */
const ZaloGlyph = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
    <path
      d="M12 3.8c-4.7 0-8.5 3.1-8.5 7 0 2.2 1.2 4.2 3.1 5.5l-.9 3.4a.4.4 0 0 0 .6.4l3.6-2a10 10 0 0 0 2.1.2c4.7 0 8.5-3.1 8.5-7s-3.8-7.5-8.5-7.5Z"
      strokeLinejoin="round"
    />
    <path d="M8.4 11.2h2.2M8.4 13.4h4.6M14.4 11.2h1.2" strokeLinecap="round" />
  </svg>
);

const LINKS = [
  {
    key: "facebook",
    href: FACEBOOK_URL,
    label: "Facebook",
    detail: "facebook.com/ndchien12",
    Glyph: FacebookGlyph,
  },
  {
    key: "zalo",
    // zalo.me resolves a bare phone number straight to the chat, on both the
    // app and the web client.
    href: `https://zalo.me/${ZALO_PHONE}`,
    label: "Zalo",
    detail: ZALO_PHONE,
    Glyph: ZaloGlyph,
  },
];

export default function SocialRail() {
  return (
    <nav className="social-rail" aria-label="Liên hệ">
      <p className="social-rail-heading">Liên hệ</p>

      <ul className="social-rail-list">
        {LINKS.map(({ key, href, label, detail, Glyph }) => (
          <li key={key}>
            {/* rel is not optional here: `noopener` keeps the opened tab from
                reaching back into this one through window.opener. */}
            <a
              className={`social-link social-link-${key}`}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
            >
              <span className="social-link-glyph">
                <Glyph />
              </span>

              {/* Read out by screen readers and shown on hover; on the wide
                  layout the rail is only as wide as the glyph. */}
              <span className="social-link-text">
                <strong>{label}</strong>
                <small>{detail}</small>
              </span>
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
