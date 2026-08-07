import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import ReaderLayout from "./components/ReaderLayout";
import { RequireAdmin } from "./components/RouteGuards";
import AuthProvider from "./context/AuthProvider";
import ThemeProvider from "./context/ThemeProvider";

import HomePage from "./pages/HomePage";
import StoriesPage from "./pages/StoriesPage";
import StoryDetailPage from "./pages/StoryDetailPage";
import ChapterPage from "./pages/ChapterPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import NotFoundPage from "./pages/NotFoundPage";

import AdminLayout from "./pages/admin/AdminLayout";
import AdminStoriesPage from "./pages/admin/AdminStoriesPage";
import AdminStoryFormPage from "./pages/admin/AdminStoryFormPage";
import AdminChaptersPage from "./pages/admin/AdminChaptersPage";
import AdminChapterFormPage from "./pages/admin/AdminChapterFormPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";

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
              <Route path="truyen/:storyId" element={<StoryDetailPage />} />
              <Route path="dang-nhap" element={<LoginPage />} />
              <Route path="dang-ky" element={<RegisterPage />} />

              {/* Admin routes. Mirrored by hasRole('ADMIN') on the server. */}
              <Route element={<RequireAdmin />}>
                <Route path="admin" element={<AdminLayout />}>
                  <Route index element={<AdminStoriesPage />} />
                  <Route path="thanh-vien" element={<AdminUsersPage />} />
                </Route>

                <Route path="admin/truyen/moi" element={<AdminStoryFormPage />} />
                <Route path="admin/truyen/:storyId" element={<AdminStoryFormPage />} />
                <Route path="admin/truyen/:storyId/chuong" element={<AdminChaptersPage />} />
                <Route path="admin/truyen/:storyId/chuong/moi" element={<AdminChapterFormPage />} />
                <Route path="admin/chuong/:chapterId" element={<AdminChapterFormPage />} />
              </Route>

              <Route path="404" element={<NotFoundPage />} />

              <Route path="*" element={<Navigate to="/404" replace />} />
            </Route>

            {/* The reader gets its own shell: it fills the viewport and
                scrolls inside its panes, so it takes no page padding. */}
            <Route element={<ReaderLayout />}>
              <Route path="chuong/:chapterId" element={<ChapterPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
