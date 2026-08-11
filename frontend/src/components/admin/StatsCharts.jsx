import { useState } from "react";
import {
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from "chart.js";
import { Bar, Line } from "react-chartjs-2";
import { Button } from "../ui";
import { barValueLabels, baseOptions, chartTheme } from "./chartTheme";

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  LineElement,
  PointElement,
  Filler,
  Legend,
  Tooltip,
);

/** dd/MM — the year is the same for every point in a two-week window. */
function shortDate(iso) {
  const [, month, day] = iso.split("-");
  return `${day}/${month}`;
}

/**
 * The three charts of §4.7, each with a table underneath it.
 *
 * The table is not a fallback for a broken chart — it is the channel that makes
 * the numbers reachable when colour is not: screen readers, print, and the
 * contrast relief rule all lean on it. It stays collapsed so it never competes
 * with the chart for the same glance.
 */
export default function StatsCharts({ charts, isDark }) {
  const theme = chartTheme(isDark);

  return (
    <div className="admin-charts">
      <ChartCard
        title="Lượt đọc / nghe theo ngày"
        hint="14 ngày gần nhất"
        table={
          <DataTable
            head={["Ngày", "Đọc", "Nghe"]}
            rows={charts.perDay.map((point) => [shortDate(point.date), point.read, point.listen])}
          />
        }
      >
        <DailyChart points={charts.perDay} theme={theme} />
      </ChartCard>

      <ChartCard
        title="Truyện được xem nhiều nhất"
        hint="Tổng lượt xem tích lũy"
        table={
          <DataTable
            head={["Truyện", "Lượt xem"]}
            rows={charts.topStories.map((story) => [story.title, story.viewCount])}
          />
        }
      >
        <TopStoriesChart stories={charts.topStories} theme={theme} />
      </ChartCard>

      <ChartCard
        title="Tỷ lệ chương theo mức khóa"
        hint="Toàn bộ chương hiện có"
        table={
          <DataTable
            head={["Mức khóa", "Số chương"]}
            rows={charts.accessLevels.map((slice) => [slice.label, slice.chapters])}
          />
        }
      >
        <AccessLevelChart slices={charts.accessLevels} theme={theme} />
      </ChartCard>
    </div>
  );
}

function ChartCard({ title, hint, children, table }) {
  const [showTable, setShowTable] = useState(false);

  return (
    <section className="admin-panel admin-chart-card">
      <div className="admin-panel-head">
        <span className="admin-panel-title">{title}</span>
        <span className="muted" style={{ fontSize: "0.78rem", fontWeight: 500 }}>
          {hint}
        </span>
        <div className="grow" />
        <Button size="sm" variant="ghost" onClick={() => setShowTable((open) => !open)}>
          {showTable ? "Ẩn bảng" : "Xem dạng bảng"}
        </Button>
      </div>

      <div className="admin-chart-body">{children}</div>

      {showTable && <div className="admin-chart-table scroll-area">{table}</div>}
    </section>
  );
}

/**
 * Two series over time → two categorical hues, plus a legend, so identity never
 * rests on colour alone.
 */
function DailyChart({ points, theme }) {
  const data = {
    labels: points.map((point) => shortDate(point.date)),
    datasets: [
      {
        label: "Lượt đọc",
        data: points.map((point) => point.read),
        borderColor: theme.series[0],
        backgroundColor: theme.series[0],
        borderWidth: 2,
        tension: 0.3,
        // No dot per point at this density; the hover target stays generous.
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBorderWidth: 2,
        pointHoverBorderColor: theme.surface,
        hitRadius: 12,
      },
      {
        label: "Lượt nghe",
        data: points.map((point) => point.listen),
        borderColor: theme.series[1],
        backgroundColor: theme.series[1],
        borderWidth: 2,
        tension: 0.3,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBorderWidth: 2,
        pointHoverBorderColor: theme.surface,
        hitRadius: 12,
      },
    ],
  };

  return <Line data={data} options={baseOptions(theme)} />;
}

/**
 * Nominal items ranked by one measure → one hue for every bar.
 *
 * Colouring each story differently would spend the identity channel re-encoding
 * what bar length already says, and a legend of eight story titles would repeat
 * the axis word for word.
 */
function TopStoriesChart({ stories, theme }) {
  const options = {
    ...baseOptions(theme),
    indexAxis: "y",
    plugins: {
      ...baseOptions(theme).plugins,
      // One series: the card title already names what is plotted.
      legend: { display: false },
      barValueLabels: { insideColor: "#ffffff", outsideColor: theme.muted },
    },
    scales: {
      x: {
        beginAtZero: true,
        grid: { color: theme.grid, drawTicks: false },
        border: { display: false },
        ticks: { color: theme.muted, font: { size: 11 }, precision: 0 },
      },
      y: {
        grid: { display: false },
        border: { color: theme.axis },
        ticks: { color: theme.muted, font: { size: 11 }, crossAlign: "far" },
      },
    },
  };

  const data = {
    labels: stories.map((story) => story.title),
    datasets: [
      {
        label: "Lượt xem",
        data: stories.map((story) => story.viewCount),
        backgroundColor: theme.series[0],
        // Rounded at the data end, square where it meets the baseline.
        borderRadius: { topLeft: 0, bottomLeft: 0, topRight: 4, bottomRight: 4 },
        borderSkipped: "start",
        barThickness: 18,
      },
    ],
  };

  return <Bar data={data} options={options} plugins={[barValueLabels]} />;
}

/**
 * Part-to-whole across an ordered ladder → one horizontal stacked bar on a
 * single-hue ramp.
 *
 * PUBLIC → MEMBER → VIP is a tier ladder, not a set of unrelated names: swapping
 * two of them would change what the chart says. A ramp puts that order in the
 * colour itself, which a three-colour pie cannot do.
 */
function AccessLevelChart({ slices, theme }) {
  const total = slices.reduce((sum, slice) => sum + slice.chapters, 0);

  const options = {
    ...baseOptions(theme),
    indexAxis: "y",
    plugins: {
      ...baseOptions(theme).plugins,
      tooltip: {
        ...baseOptions(theme).plugins.tooltip,
        callbacks: {
          label: (context) => {
            const value = context.parsed.x;
            const share = total ? Math.round((value / total) * 100) : 0;
            return ` ${context.dataset.label}: ${value} chương (${share}%)`;
          },
        },
      },
    },
    scales: {
      x: {
        stacked: true,
        display: false,
        max: total || 1,
      },
      y: { stacked: true, display: false },
    },
  };

  const data = {
    labels: [""],
    datasets: slices.map((slice, index) => ({
      label: slice.label,
      data: [slice.chapters],
      backgroundColor: theme.ramp[index],
      // A 2px gap in the surface colour is what separates touching segments —
      // not an outline, which would add ink that carries no data.
      borderColor: theme.surface,
      borderWidth: 2,
      borderRadius: 4,
      barThickness: 44,
    })),
  };

  return <Bar data={data} options={options} />;
}

function DataTable({ head, rows }) {
  return (
    <table className="nb-table">
      <thead>
        <tr>
          {head.map((cell) => (
            <th key={cell}>{cell}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row[0]}>
            {row.map((cell, index) => (
              <td key={index} className={index === 0 ? "" : "tabular-num"}>
                {typeof cell === "number" ? cell.toLocaleString("vi-VN") : cell}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
