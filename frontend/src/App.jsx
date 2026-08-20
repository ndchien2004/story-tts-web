import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AuthLayout from "./components/AuthLayout";
import Layout from "./components/Layout";
import ReaderLayout from "./components/ReaderLayout";
import { RequireAdmin, RequireAuth, RequireGuest } from "./components/RouteGuards";
import RouteVeil from "./components/RouteVeil";
import SessionGuard from "./components/SessionGuard";
import SupportWidget from "./components/support/SupportWidget";
import AuthProvider from "./context/AuthProvider";
import NotificationProvider from "./context/NotificationProvider";
import SupportSocketProvider from "./context/SupportSocketProvider";
import ThemeProvider from "./context/ThemeProvider";

import HomePage from "./pages/HomePage";
import StoriesPage from "./pages/StoriesPage";
import StoryDetailPage from "./pages/StoryDetailPage";
import ChapterPage from "./pages/ChapterPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import AccountPage from "./pages/AccountPage";
import UpgradePage from "./pages/UpgradePage";
import TopUpPage from "./pages/TopUpPage";
import PaymentResultPage from "./pages/PaymentResultPage";
import NotFoundPage from "./pages/NotFoundPage";

import AdminLayout from "./pages/admin/AdminLayout";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminCommentsPage from "./pages/admin/AdminCommentsPage";
import AdminStoriesPage from "./pages/admin/AdminStoriesPage";
import AdminStoryFormPage from "./pages/admin/AdminStoryFormPage";
import AdminChaptersPage from "./pages/admin/AdminChaptersPage";
import AdminChapterFormPage from "./pages/admin/AdminChapterFormPage";
import AdminCatalogPage from "./pages/admin/AdminCatalogPage";
import AdminBgmPage from "./pages/admin/AdminBgmPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminNotificationsPage from "./pages/admin/AdminNotificationsPage";
import AdminSupportPage from "./pages/admin/AdminSupportPage";
import AdminVipPage from "./pages/admin/AdminVipPage";
import AdminVipPlansPage from "./pages/admin/AdminVipPlansPage";
import AdminCoinPackagesPage from "./pages/admin/AdminCoinPackagesPage";
import AdminGiftCodesPage from "./pages/admin/AdminGiftCodesPage";

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        {/* Inside AuthProvider, above every route: the inbox belongs to the
            session rather than to any one screen, and the bell that reads it
            is in the header of all four layouts. Its whole state is keyed on
            the signed-in account, so signing out clears it and signing in as
            someone else rebuilds it from scratch. */}
        <NotificationProvider>
          {/* Một kết nối WebSocket hỗ trợ cho cả tab, nằm trên mọi tuyến.
              Ba bên dùng chung nó: bong bóng chat ở góc màn hình, hộp thư của
              quản trị viên, và khung hội thoại đang mở. Mỗi bên tự mở lấy một
              kết nối thì chỉ vài tab là chạm trần theo tài khoản — và quan
              trọng hơn: kết nối sẽ chỉ sống khi có khung chat đang mở, nên
              trang quản trị ngồi nhìn danh sách sẽ không nhận được gì. */}
          <SupportSocketProvider>
          <BrowserRouter>
            {/* Outside <Routes> on purpose: it has to survive the route swap it
                is covering, and it spans all four layouts. */}
            <RouteVeil />

            {/* Same reasoning, one step further: this one causes the navigation
                it has to survive. It is also the only thing that reacts to a
                session the server has ended, so it must sit above every route
                rather than inside any of them. */}
            <SessionGuard />

            {/* Cùng lý do với hai thứ trên: nó ở góc mọi trang và phải sống
                qua mọi lần đổi tuyến. Nó tự ẩn với khách, với quản trị viên,
                và trên trang đọc chương — nơi trợ lý AI đã chiếm đúng góc
                này. Xem SupportWidget. */}
            <SupportWidget />

            <Routes>
              <Route element={<Layout />}>
                {/* Public reader routes. Chapter access is decided by the API. */}
                <Route index element={<HomePage />} />
                <Route path="truyen" element={<StoriesPage />} />

                {/* The price list is public on purpose: it is what someone reads
                    before deciding whether to sign up at all. The buy button
                    behind it still needs an account. */}
                <Route path="nang-cap" element={<UpgradePage />} />
                <Route path="nap-xu" element={<TopUpPage />} />

                {/* Where PayOS returns the reader to. Its query string is never
                    trusted — the page asks our own server what happened. */}
                <Route path="thanh-toan/ket-qua" element={<PaymentResultPage />} />

                <Route path="404" element={<NotFoundPage />} />

                <Route path="*" element={<Navigate to="/404" replace />} />
              </Route>

              {/* Signing in and up share a shell of their own: a split screen
                  sized to the viewport, so neither page scrolls. */}
              <Route element={<RequireGuest />}>
                <Route element={<AuthLayout />}>
                  <Route path="dang-nhap" element={<LoginPage />} />
                  <Route path="dang-ky" element={<RegisterPage />} />
                  <Route path="quen-mat-khau" element={<ForgotPasswordPage />} />

                  {/* Where the link in the reset email lands. Kept in step with
                      `app.mail.reset-url` on the server, which builds that link. */}
                  <Route path="dat-lai-mat-khau" element={<ResetPasswordPage />} />
                </Route>
              </Route>

              {/* Reading screens get their own shell: they fill the viewport and
                  scroll inside their panes, so they take no page padding. */}
              <Route element={<ReaderLayout />}>
                <Route path="truyen/:storyId" element={<StoryDetailPage />} />
                <Route path="chuong/:chapterId" element={<ChapterPage />} />

                {/* Profile and shelf are one page: both answer "my stuff", and
                    apart the profile was four facts on a screen of its own. Two
                    paths reach it so neither the account button nor the older
                    "Tủ truyện" link has to give way. */}
                <Route element={<RequireAuth />}>
                  <Route path="tai-khoan" element={<AccountPage />} />
                  <Route path="tu-truyen" element={<AccountPage />} />
                </Route>
              </Route>

              {/* The admin console is a shell of its own, outside the reader
                  layout on purpose: managing content and browsing it never share
                  a navigation bar. Mirrored by hasRole('ADMIN') on the server. */}
              <Route element={<RequireAdmin />}>
                <Route path="admin" element={<AdminLayout />}>
                  {/* The console opens on the overview: the story list answered
                      "what is there" but never "what needs doing". */}
                  <Route index element={<AdminDashboardPage />} />
                  <Route path="truyen" element={<AdminStoriesPage />} />
                  <Route path="danh-muc" element={<AdminCatalogPage />} />
                  {/* The shared background-music library. Readers pick from it on
                      the reading page; without it that box is empty for anyone
                      who has no music of their own. */}
                  <Route path="nhac-nen" element={<AdminBgmPage />} />
                  <Route path="binh-luan" element={<AdminCommentsPage />} />
                  <Route path="thanh-vien" element={<AdminUsersPage />} />
                  {/* Soạn tin gửi người đọc. Bốn loại thông báo còn lại không
                      có màn hình nào: chúng sinh ra bên trong giao dịch nghiệp
                      vụ đã tạo ra chúng. */}
                  <Route path="thong-bao" element={<AdminNotificationsPage />} />
                  {/* Hộp thư hỗ trợ dùng chung của cả đội — hàng đợi, không phải
                      hộp thư riêng của từng người. Mirrored by hasRole('ADMIN')
                      on the server, which guards all of /api/admin. */}
                  <Route path="ho-tro" element={<AdminSupportPage />} />
                  <Route path="vip" element={<AdminVipPage />} />
                  {/* The price list on its own page — see AdminVipPlansPage. */}
                  <Route path="vip/goi" element={<AdminVipPlansPage />} />
                  <Route path="xu/goi" element={<AdminCoinPackagesPage />} />
                  {/* Cùng nhánh với gói nạp: hai cách Xu vào ví người đọc — một
                      cái bán, một cái phát. Mirrored by hasRole('ADMIN') on the
                      server, which guards all of /api/admin. */}
                  <Route path="xu/ma-qua-tang" element={<AdminGiftCodesPage />} />
                  <Route path="truyen/moi" element={<AdminStoryFormPage />} />
                  <Route path="truyen/:storyId" element={<AdminStoryFormPage />} />
                  <Route path="truyen/:storyId/chuong" element={<AdminChaptersPage />} />
                  <Route path="truyen/:storyId/chuong/moi" element={<AdminChapterFormPage />} />
                  <Route path="chuong/:chapterId" element={<AdminChapterFormPage />} />
                </Route>
              </Route>
            </Routes>
          </BrowserRouter>
          </SupportSocketProvider>
        </NotificationProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
