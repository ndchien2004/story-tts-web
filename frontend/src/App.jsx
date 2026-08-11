import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import AuthLayout from "./components/AuthLayout";
import Layout from "./components/Layout";
import ReaderLayout from "./components/ReaderLayout";
import { RequireAdmin, RequireAuth, RequireGuest } from "./components/RouteGuards";
import AuthProvider from "./context/AuthProvider";
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
import PaymentResultPage from "./pages/PaymentResultPage";
import NotFoundPage from "./pages/NotFoundPage";

import AdminLayout from "./pages/admin/AdminLayout";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminAudioPage from "./pages/admin/AdminAudioPage";
import AdminCommentsPage from "./pages/admin/AdminCommentsPage";
import AdminStoriesPage from "./pages/admin/AdminStoriesPage";
import AdminStoryFormPage from "./pages/admin/AdminStoryFormPage";
import AdminChaptersPage from "./pages/admin/AdminChaptersPage";
import AdminChapterFormPage from "./pages/admin/AdminChapterFormPage";
import AdminCatalogPage from "./pages/admin/AdminCatalogPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminVipPage from "./pages/admin/AdminVipPage";

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              {/* Public reader routes. Chapter access is decided by the API. */}
              <Route index element={<HomePage />} />
              <Route path="truyen" element={<StoriesPage />} />

              {/* The price list is public on purpose: it is what someone reads
                  before deciding whether to sign up at all. The buy button
                  behind it still needs an account. */}
              <Route path="nang-cap" element={<UpgradePage />} />

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
                <Route path="binh-luan" element={<AdminCommentsPage />} />
                <Route path="audio" element={<AdminAudioPage />} />
                <Route path="thanh-vien" element={<AdminUsersPage />} />
                <Route path="vip" element={<AdminVipPage />} />
                <Route path="truyen/moi" element={<AdminStoryFormPage />} />
                <Route path="truyen/:storyId" element={<AdminStoryFormPage />} />
                <Route path="truyen/:storyId/chuong" element={<AdminChaptersPage />} />
                <Route path="truyen/:storyId/chuong/moi" element={<AdminChapterFormPage />} />
                <Route path="chuong/:chapterId" element={<AdminChapterFormPage />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
