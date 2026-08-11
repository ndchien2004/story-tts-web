const STARS = [1, 2, 3, 4, 5];

function Star({ filled, half }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="star-icon">
      {/* A half star is the same outline with the fill clipped to its left side,
          so an average of 3.5 does not have to round to a whole star. */}
      {half && (
        <defs>
          <clipPath id="star-half-clip">
            <rect x="0" y="0" width="12" height="24" />
          </clipPath>
        </defs>
      )}
      <path
        className="star-outline"
        d="m12 2.6 2.9 5.9 6.5.95-4.7 4.58 1.1 6.47L12 17.44 6.2 20.5l1.1-6.47L2.6 9.45l6.5-.95z"
      />
      {(filled || half) && (
        <path
          className="star-fill"
          clipPath={half ? "url(#star-half-clip)" : undefined}
          d="m12 2.6 2.9 5.9 6.5.95-4.7 4.58 1.1 6.47L12 17.44 6.2 20.5l1.1-6.47L2.6 9.45l6.5-.95z"
        />
      )}
    </svg>
  );
}

/**
 * Five stars, either as a read-only score or as a picker.
 *
 * Passing `onChange` turns it into a radio group: each star is a real radio
 * input, so arrow keys and screen readers behave the way they do in any other
 * form control. Without it the stars are decoration beside a number.
 */
export default function StarRating({ value = 0, onChange, name = "rating", size = "md" }) {
  const readOnly = !onChange;

  if (readOnly) {
    return (
      <span className={`star-rating star-rating-${size}`} role="img" aria-label={`${value} trên 5 sao`}>
        {STARS.map((star) => (
          <Star key={star} filled={value >= star} half={value >= star - 0.5 && value < star} />
        ))}
      </span>
    );
  }

  return (
    <span className={`star-rating star-rating-${size} star-rating-input`} role="radiogroup">
      {STARS.map((star) => (
        <label key={star} className="star-option" title={`${star} sao`}>
          <input
            type="radio"
            name={name}
            value={star}
            checked={value === star}
            onChange={() => onChange(star)}
          />
          <span className="sr-only">{star} sao</span>
          <Star filled={value >= star} />
        </label>
      ))}
    </span>
  );
}
