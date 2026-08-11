/**
 * Brand artwork, served from Cloudinary.
 *
 * The two source PNGs sit under `story-tts-web/brand`; everything below is the
 * same two files with a transformation chain in front:
 *
 * - `e_trim` strips the wide flat margin the artwork was exported with, so the
 *   mark fills the space it is given instead of floating in it.
 * - `co_white,e_make_transparent` drops the flat backdrop the logo was drawn
 *   on. Without it the mark can only sit on a light tile of its own — on the
 *   dark theme a bare logo would show as a white rectangle. Doing it here
 *   rather than in CSS means one asset that is simply correct on any
 *   background.
 * - `c_fit` caps the delivered size — the sources are 450px and 1408px wide and
 *   nothing here is drawn larger than a navbar.
 * - `f_auto,q_auto` lets Cloudinary pick the format and quality per browser;
 *   every format it can choose from carries an alpha channel.
 *
 * The tolerance is deliberately low. At 45 the effect also ate the cream page
 * of the book and part of the outline; at 25 the backdrop goes and the artwork
 * survives whole.
 */
const CLOUDINARY = "https://res.cloudinary.com/dzwimbvjh/image/upload";
const CUTOUT = "e_trim:12/co_white,e_make_transparent:25";

/** Circular mark on its own — the navbar and the browser tab. */
export const LOGO_MARK = `${CLOUDINARY}/${CUTOUT}/c_fit,w_160,h_160/f_auto,q_auto/story-tts-web/brand/logo-mark.png`;

/** Mark plus the wordmark and tagline, for places with room to spare. */
export const LOGO_FULL = `${CLOUDINARY}/${CUTOUT}/c_fit,w_640/f_auto,q_auto/story-tts-web/brand/logo-full.png`;

/** Tab icon. PNG explicitly: `f_auto` could answer with a format no favicon accepts. */
export const FAVICON = `${CLOUDINARY}/${CUTOUT}/c_fit,w_64,h_64/f_png/story-tts-web/brand/logo-mark.png`;

/**
 * Home page banner, delivered whole.
 *
 * No cutout — the artwork is a finished composition on its own ground, not a
 * mark that has to sit on whatever colour is behind it.
 *
 * No crop and no resize either: the page shows the entire image edge to edge,
 * so any `c_fill` would cut off part of what the artwork is saying, and any
 * width cap would throw away pixels the layout can use. `q_auto:best` keeps
 * compression off the illustration's flat areas, where ordinary `q_auto`
 * leaves visible banding.
 *
 * The version prefix is kept: replacing the artwork under the same public id
 * would otherwise leave the old one sitting in every CDN and browser cache.
 */
export const HERO_BANNER = `${CLOUDINARY}/f_auto,q_auto:best/v1786431511/hero_banner_iekpd5.png`;
