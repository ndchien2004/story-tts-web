/**
 * Normalises every string in an API payload to Unicode NFC.
 *
 * Vietnamese can be encoded two ways: precomposed (NFC, "ế" as one code point)
 * or decomposed (NFD, "e" followed by two combining marks). Browsers render
 * decomposed text by stacking marks that carry their own advance width, so a
 * word ends up looking spaced out and broken. Folding to NFC at the boundary
 * means the rest of the app never has to think about it.
 */

/** Guards against pathological nesting in an unexpected payload. */
const MAX_DEPTH = 12;

export function normalizeText(value) {
  return typeof value === "string" ? value.normalize("NFC") : value;
}

export function normalizeDeep(value, depth = 0) {
  if (depth > MAX_DEPTH) return value;

  if (typeof value === "string") {
    return value.normalize("NFC");
  }

  if (Array.isArray(value)) {
    return value.map((item) => normalizeDeep(item, depth + 1));
  }

  // Plain objects only; Date, File and friends are returned untouched.
  if (value !== null && typeof value === "object" && value.constructor === Object) {
    const result = {};
    for (const [key, item] of Object.entries(value)) {
      result[key] = normalizeDeep(item, depth + 1);
    }
    return result;
  }

  return value;
}
