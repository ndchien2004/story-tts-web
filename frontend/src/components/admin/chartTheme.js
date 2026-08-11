/**
 * Colours and shared options for every chart in the admin console.
 *
 * The app is monochrome, so the marks are too: series are told apart by
 * lightness rather than by hue. That is not a compromise for accessibility —
 * a lightness ladder is legible under every form of colour blindness by
 * construction, which the blue/orange pair it replaces only achieved after
 * being measured.
 *
 * What still has to be checked is contrast against the chart's own surface.
 * Every value below clears the 3:1 floor for a non-text mark:
 *
 *   categorical, light  #111113 (18.9:1) / #949499 (3.0:1)
 *   categorical, dark   #ededf0 (16.4:1) / #8b8b94 (5.7:1)
 *   ordinal ramp, light #949499 → #5c5c63 → #111113  (3.0 / 6.9 / 18.9)
 *   ordinal ramp, dark  #6b6b73 → #a1a1aa → #ededf0  (3.6 / 7.6 / 16.4)
 *
 * Each mode is a selected set of steps, not an inversion of the other: the
 * ramps run in opposite directions because the ground they sit on does.
 */

const LIGHT = {
  surface: "#ffffff",
  ink: "#1c1c21",
  muted: "#6e6e78",
  grid: "#e7e7ea",
  axis: "#d3d3da",
  // Categorical: identity. Slot 1 also serves single-series bars.
  series: ["#1c1c21", "#949499"],
  // Ordinal: PUBLIC → MEMBER → VIP is a ladder, so the value carries the order.
  ramp: ["#949499", "#5c5c63", "#1c1c21"],
};

const DARK = {
  surface: "#1c1d22",
  ink: "#e8e9ed",
  muted: "#9a9ba5",
  grid: "#2e3037",
  axis: "#3d4048",
  series: ["#e8e9ed", "#9a9ba5"],
  ramp: ["#75767f", "#a8a9b3", "#e8e9ed"],
};

export function chartTheme(isDark) {
  return isDark ? DARK : LIGHT;
}

/**
 * Options shared by every chart: recessive chrome, and a hover layer that does
 * not demand pixel-perfect aim.
 */
export function baseOptions(theme) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    // Hovering anywhere in a column picks that column, rather than requiring the
    // pointer to land exactly on a 2px line.
    interaction: { mode: "index", intersect: false },
    plugins: {
      legend: {
        position: "bottom",
        labels: {
          color: theme.muted,
          boxWidth: 10,
          boxHeight: 10,
          usePointStyle: true,
          pointStyle: "circle",
          padding: 16,
          font: { size: 12, weight: 500 },
        },
      },
      tooltip: {
        backgroundColor: theme.ink,
        titleColor: theme.surface,
        bodyColor: theme.surface,
        padding: 10,
        cornerRadius: 4,
        displayColors: true,
        boxWidth: 8,
        boxHeight: 8,
        usePointStyle: true,
      },
    },
    scales: {
      x: {
        grid: { display: false },
        border: { color: theme.axis },
        ticks: { color: theme.muted, font: { size: 11 } },
      },
      y: {
        beginAtZero: true,
        // Hairline, solid, one step off the surface — present but never loud.
        grid: { color: theme.grid, drawTicks: false },
        border: { display: false },
        ticks: { color: theme.muted, font: { size: 11 }, precision: 0, padding: 8 },
      },
    },
  };
}

/**
 * Draws each bar's value at its tip.
 *
 * Written inline rather than pulling in a datalabels plugin: the whole need is
 * one number per bar, and the label has to be measured before it is drawn — a
 * value that would not fit inside the bar goes just past its end instead of
 * being clipped by it.
 */
export const barValueLabels = {
  id: "barValueLabels",
  afterDatasetsDraw(chart, _args, options) {
    const { ctx } = chart;
    const meta = chart.getDatasetMeta(0);
    if (!meta?.data) return;

    ctx.save();
    ctx.font = "600 11px system-ui, -apple-system, 'Segoe UI', sans-serif";
    ctx.textBaseline = "middle";

    meta.data.forEach((bar, index) => {
      const value = chart.data.datasets[0].data[index];
      if (value === null || value === undefined) return;

      const text = Number(value).toLocaleString("vi-VN");
      const width = ctx.measureText(text).width;
      const barLength = bar.x - bar.base;

      // Inside the bar only when it comfortably fits; otherwise just outside it.
      const fitsInside = barLength > width + 16;
      ctx.fillStyle = fitsInside ? options.insideColor : options.outsideColor;
      ctx.textAlign = fitsInside ? "right" : "left";
      ctx.fillText(text, fitsInside ? bar.x - 8 : bar.x + 8, bar.y);
    });

    ctx.restore();
  },
};
