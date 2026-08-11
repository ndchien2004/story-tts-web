import { lazy, Suspense, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useTheme } from "../../context/theme-context";
import { Alert, Spinner } from "../../components/ui";

/**
 * Chart.js is a large dependency used on exactly one screen behind an admin
 * login. Loading it on demand keeps it out of the bundle every reader downloads.
 */
const StatsCharts = lazy(() => import("../../components/admin/StatsCharts"));

/** Percentage as a whole number, with the 0/0 case answering 0 rather than NaN. */
function percent(part, total) {
  if (!total) return 0;
  return Math.round((part / total) * 100);
}

/**
 * The console's landing screen.
 *
 * It opened straight onto the story list before, which answered "what is there"
 * but never "what needs doing". The tiles are ordered by that second question:
 * the audio gap and anything that failed come first, because those are the two
 * things that will not fix themselves.
 */
export default function AdminDashboardPage() {
  const { isDark } = useTheme();
  const [stats, setStats] = useState(null);
  const [charts, setCharts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    adminApi
      .stats()
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });

    // Separate call, and a failure here is not fatal: the tiles above are the
    // part an admin needs, the charts are the part they linger on.
    adminApi
      .charts(14)
      .then((data) => {
        if (!cancelled) setCharts(data);
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return (
      <AdminPage title="Tổng quan">
        <Alert tone="error">{error}</Alert>
      </AdminPage>
    );
  }

  if (!stats) {
    return (
      <AdminPage title="Tổng quan">
        <Spinner label="Đang tải số liệu…" />
      </AdminPage>
    );
  }

  const audioCoverage = percent(stats.chaptersWithAudio, stats.chapters);
  const missingAudio = stats.chapters - stats.chaptersWithAudio;

  return (
    <AdminPage title="Tổng quan">
      <div className="admin-dash">
        {/* Anything needing attention, before anything merely worth knowing. */}
        {(missingAudio > 0 || stats.audioFailed > 0 || stats.audioProcessing > 0) && (
          <section className="admin-dash-alerts">
            {missingAudio > 0 && (
              <Link to="/admin/truyen" className="admin-dash-todo">
                <strong>{missingAudio} chương chưa có audio</strong>
                <span>Mở truyện rồi lọc “Chưa có audio” để tạo hàng loạt →</span>
              </Link>
            )}
            {stats.audioFailed > 0 && (
              <Link to="/admin/truyen" className="admin-dash-todo admin-dash-todo-danger">
                <strong>{stats.audioFailed} bản audio tạo hỏng</strong>
                <span>Mở truyện để tạo lại →</span>
              </Link>
            )}
            {stats.audioProcessing > 0 && (
              <span className="admin-dash-todo admin-dash-todo-quiet">
                <strong>{stats.audioProcessing} bản audio đang tạo</strong>
                <span>Máy chủ đang xử lý nền</span>
              </span>
            )}
          </section>
        )}

        {/* Re-mounted on a theme flip: Chart.js reads its colours once when the
            canvas is built, so the same instance would keep the old mode's ink. */}
        {charts && (
          <Suspense fallback={<Spinner label="Đang tải biểu đồ…" />}>
            <StatsCharts key={isDark ? "dark" : "light"} charts={charts} isDark={isDark} />
          </Suspense>
        )}

        <StatGroup title="Nội dung">
          <Stat label="Truyện" value={stats.stories} />
          <Stat label="Đang ra" value={stats.storiesOngoing} tone="info" />
          <Stat label="Hoàn thành" value={stats.storiesCompleted} tone="success" />
          <Stat label="Chương" value={stats.chapters} />
          <Stat label="Thể loại" value={stats.genres} />
          <Stat label="Tác giả" value={stats.authors} />
        </StatGroup>

        <StatGroup title="Mức khóa chương">
          <Stat label="Công khai" value={stats.chaptersPublic} tone="success" />
          <Stat label="Thành viên" value={stats.chaptersMember} tone="info" />
          <Stat label="VIP" value={stats.chaptersVip} tone="violet" />
        </StatGroup>

        <StatGroup title="Audio">
          <Stat
            label="Độ phủ audio"
            value={`${audioCoverage}%`}
            hint={`${stats.chaptersWithAudio}/${stats.chapters} chương`}
            tone={audioCoverage >= 80 ? "success" : audioCoverage >= 40 ? "warning" : "danger"}
          />
          <Stat label="Máy tạo" value={stats.audioFromTts} />
          <Stat label="Tải lên thủ công" value={stats.audioUploaded} />
          <Stat label="Đang tạo" value={stats.audioProcessing} tone={stats.audioProcessing ? "info" : undefined} />
          <Stat label="Tạo hỏng" value={stats.audioFailed} tone={stats.audioFailed ? "danger" : undefined} />
        </StatGroup>

        <StatGroup title="Thành viên">
          <Stat label="Tài khoản" value={stats.users} />
          <Stat label="VIP" value={stats.vipUsers} tone="violet" />
          <Stat label="Quản trị viên" value={stats.admins} tone="info" />
          <Stat label="Đang bị khóa" value={stats.disabledUsers} tone={stats.disabledUsers ? "danger" : undefined} />
        </StatGroup>

        <StatGroup title="Tương tác">
          <Stat label="Bình luận & đánh giá" value={stats.comments} />
          <Stat label="Lượt yêu thích" value={stats.favorites} />
        </StatGroup>
      </div>
    </AdminPage>
  );
}

function StatGroup({ title, children }) {
  return (
    <section className="admin-panel admin-dash-group">
      <div className="admin-panel-head">
        <span className="admin-panel-title">{title}</span>
      </div>
      <div className="admin-dash-tiles">{children}</div>
    </section>
  );
}

function Stat({ label, value, hint, tone }) {
  return (
    <div className={`admin-dash-tile ${tone ? `admin-dash-tile-${tone}` : ""}`}>
      <span className="admin-dash-value tabular-num">
        {typeof value === "number" ? value.toLocaleString("vi-VN") : value}
      </span>
      <span className="admin-dash-label">{label}</span>
      {hint && <span className="admin-dash-hint">{hint}</span>}
    </div>
  );
}
