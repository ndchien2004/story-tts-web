import client, { API_BASE_URL, getStoredToken } from "./client";

/* ------------------------------------------------------------------ */
/* Auth                                                                */
/* ------------------------------------------------------------------ */

export const authApi = {
  register: (payload) => client.post("/api/auth/register", payload).then((r) => r.data),
  login: (payload) => client.post("/api/auth/login", payload).then((r) => r.data),
  me: () => client.get("/api/auth/me").then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Catalog                                                             */
/* ------------------------------------------------------------------ */

export const catalogApi = {
  genres: () => client.get("/api/genres").then((r) => r.data),
  authors: () => client.get("/api/authors").then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Reader                                                              */
/* ------------------------------------------------------------------ */

export const storyApi = {
  /** `params` accepts keyword, genreId, status, sort, page and size. */
  list: (params) => client.get("/api/stories", { params }).then((r) => r.data),
  detail: (id) => client.get(`/api/stories/${id}`).then((r) => r.data),
  chapters: (id) => client.get(`/api/stories/${id}/chapters`).then((r) => r.data),
};

export const chapterApi = {
  detail: (id) => client.get(`/api/chapters/${id}`).then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Audio and speech synthesis                                          */
/* ------------------------------------------------------------------ */

export const audioApi = {
  list: (chapterId) => client.get(`/api/chapters/${chapterId}/audio`).then((r) => r.data),

  voices: (chapterId) => client.get(`/api/chapters/${chapterId}/tts/voices`).then((r) => r.data),

  /** Starts synthesis, or returns the cached track when one already exists. */
  requestTts: (chapterId, { voice, speed }) =>
    client.post(`/api/chapters/${chapterId}/tts`, { voice, speed }).then((r) => r.data),

  ttsStatus: (chapterId, audioId) =>
    client.get(`/api/chapters/${chapterId}/tts/${audioId}/status`).then((r) => r.data),

  /**
   * Builds the URL for an `<audio>` element.
   *
   * The element cannot send an Authorization header, so the token travels as a
   * query parameter that the server's JWT filter also accepts.
   */
  streamUrl: (path) => {
    const token = getStoredToken();
    const url = new URL(path, API_BASE_URL);
    if (token) {
      url.searchParams.set("access_token", token);
    }
    return url.toString();
  },
};

/* ------------------------------------------------------------------ */
/* Admin                                                               */
/* ------------------------------------------------------------------ */

export const adminApi = {
  createStory: (payload) => client.post("/api/admin/stories", payload).then((r) => r.data),
  updateStory: (id, payload) => client.put(`/api/admin/stories/${id}`, payload).then((r) => r.data),
  deleteStory: (id) => client.delete(`/api/admin/stories/${id}`),

  createChapter: (storyId, payload) =>
    client.post(`/api/admin/stories/${storyId}/chapters`, payload).then((r) => r.data),

  uploadAudio: (chapterId, file) => {
    const form = new FormData();
    form.append("file", file);
    return client
      .post(`/api/admin/chapters/${chapterId}/audio`, form, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => r.data);
  },
  deleteAudio: (audioId) => client.delete(`/api/admin/audio/${audioId}`),
  getChapter: (id) => client.get(`/api/admin/chapters/${id}`).then((r) => r.data),
  updateChapter: (id, payload) => client.put(`/api/admin/chapters/${id}`, payload).then((r) => r.data),
  setChapterAccessLevel: (id, accessLevel) =>
    client.patch(`/api/admin/chapters/${id}/access-level`, { accessLevel }).then((r) => r.data),
  deleteChapter: (id) => client.delete(`/api/admin/chapters/${id}`),

  listUsers: (params) => client.get("/api/admin/users", { params }).then((r) => r.data),
  setVip: (id, value) => client.patch(`/api/admin/users/${id}/vip`, { value }).then((r) => r.data),
  setEnabled: (id, value) => client.patch(`/api/admin/users/${id}/enabled`, { value }).then((r) => r.data),

  createGenre: (payload) => client.post("/api/admin/genres", payload).then((r) => r.data),
  updateGenre: (id, payload) => client.put(`/api/admin/genres/${id}`, payload).then((r) => r.data),
  deleteGenre: (id) => client.delete(`/api/admin/genres/${id}`),

  createAuthor: (payload) => client.post("/api/admin/authors", payload).then((r) => r.data),
  updateAuthor: (id, payload) => client.put(`/api/admin/authors/${id}`, payload).then((r) => r.data),
  deleteAuthor: (id) => client.delete(`/api/admin/authors/${id}`),
};
