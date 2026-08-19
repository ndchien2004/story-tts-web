import client, { API_BASE_URL, getStoredToken } from "./client";

/* ------------------------------------------------------------------ */
/* Auth                                                                */
/* ------------------------------------------------------------------ */

export const authApi = {
  /**
   * Starts a registration. No account exists yet: the answer says whether a
   * code went out, and carries a session only when the server has no mail
   * account and therefore no way to verify anything.
   */
  register: (payload) => client.post("/api/auth/register", payload).then((r) => r.data),

  /** Completes it. This is the call that puts the account in the database. */
  verifyRegistration: (payload) =>
    client.post("/api/auth/register/verify", payload).then((r) => r.data),

  resendRegistrationCode: (email) =>
    client.post("/api/auth/register/resend", { email }).then((r) => r.data),

  login: (payload) => client.post("/api/auth/login", payload).then((r) => r.data),
  me: () => client.get("/api/auth/me").then((r) => r.data),

  /**
   * Which sign-in methods the server has configured, plus the Google client id.
   *
   * The id is served rather than duplicated in the frontend build, so the OAuth
   * application is declared in exactly one place.
   */
  providers: () => client.get("/api/auth/providers").then((r) => r.data),

  /** `credential` is the ID token Google Identity Services handed the browser. */
  google: (credential) => client.post("/api/auth/google", { credential }).then((r) => r.data),

  forgotPassword: (email) =>
    client.post("/api/auth/forgot-password", { email }).then((r) => r.data),

  resetPassword: (payload) =>
    client.post("/api/auth/reset-password", payload).then((r) => r.data),
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

  /**
   * Listen ranking for a window. `params` takes period (day/week/month) and limit.
   *
   * Counts the listens inside that window rather than the running total, so the
   * three periods genuinely disagree with each other.
   */
  topListened: (params) =>
    client.get("/api/stories/top-listened", { params }).then((r) => r.data),

  detail: (id) => client.get(`/api/stories/${id}`).then((r) => r.data),
  /**
   * One page of a story's chapters.
   *
   * Paged rather than whole: `detail` already carries the first page, and a
   * thousand-chapter translation used to arrive in a single response — a few
   * hundred KB of JSON on every visit to the story page.
   *
   * `q` searches chapter titles, or jumps straight to a number when it is one.
   */
  chapters: (id, params) =>
    client.get(`/api/stories/${id}/chapters`, { params }).then((r) => r.data),
};

export const chapterApi = {
  detail: (id) => client.get(`/api/chapters/${id}`).then((r) => r.data),

  /**
   * Whether this reader may open the chapter, and if not, what it would take.
   *
   * Asked *before* the content call rather than instead of it. Reading the
   * refusal off a failed `detail` would work, but it makes an error the normal
   * path for a chapter that is simply for sale — and it cannot say how many Xu
   * short the reader is, which is the one number the unlock screen needs.
   */
  access: (id) => client.get(`/api/chapters/${id}/access`).then((r) => r.data),

  /**
   * Spends Xu to open a chapter.
   *
   * Safe to call twice: the server answers ALREADY_OWNED rather than charging
   * again, so a double click or a retried request costs nothing.
   */
  purchase: (id) => client.post(`/api/chapters/${id}/purchase`).then((r) => r.data),

  /**
   * Absolute URL of the chapter's update stream, for `EventSource`.
   *
   * No token on it, and that is the reason it is a plain URL rather than a
   * `client` call: `EventSource` cannot set an `Authorization` header, so an
   * authenticated stream would mean putting the token in the query string —
   * where it lands in access logs and browser history. The endpoint carries
   * nothing private (a chapter id, a story id and a version number), so it does
   * not need one. See `ChapterController.events`.
   */
  eventsUrl: (id) => new URL(`/api/chapters/${id}/events`, API_BASE_URL).toString(),
};

/* ------------------------------------------------------------------ */
/* Audio and speech synthesis                                          */
/* ------------------------------------------------------------------ */

export const audioApi = {
  list: (chapterId) => client.get(`/api/chapters/${chapterId}/audio`).then((r) => r.data),

  /**
   * Asks the server to narrate a chapter that has no audio yet.
   *
   * Comes back straight away: READY when a usable track already existed (which
   * costs the reader nothing), otherwise PROCESSING to be polled. Voice and
   * speed are the server's to choose — they are part of the cache key, so
   * letting readers pick would mint a new file per preference per chapter.
   */
  requestTts: (chapterId) => client.post(`/api/chapters/${chapterId}/tts`).then((r) => r.data),

  /**
   * State of one track, for the wait after a generation is queued.
   *
   * Deliberately not `list` — that endpoint counts a listen every time it is
   * called, and polling it would invent dozens of them per chapter.
   */
  ttsStatus: (chapterId, audioId) =>
    client.get(`/api/chapters/${chapterId}/tts/${audioId}`).then((r) => r.data),

  /**
   * Mốc thời gian từng chữ của một bản audio, cho phần tô sáng theo giọng đọc.
   *
   * Một lời gọi cho cả chương, gọi lúc mở chương. Cố ý không có đường nào hỏi
   * theo nhịp phát: dò tìm là việc của trình duyệt, và một yêu cầu mạng mỗi
   * giây sẽ biến phần tô sáng thành thứ giật theo chất lượng đường truyền.
   *
   * Bản không có mốc trả về `timestamps` rỗng chứ không báo lỗi — nên chỗ đáng
   * hỏi trước là cờ `hasTranscript` trong danh sách bản audio.
   */
  transcript: (chapterId, audioId) =>
    client.get(`/api/chapters/${chapterId}/audio/${audioId}/transcript`).then((r) => r.data),

  /**
   * Tải một bản audio về máy người nghe.
   *
   * Không phải một thẻ `<a download>` trỏ thẳng vào đường phát, dù đó là cách
   * ngắn nhất: đường phát nằm ở tên miền của máy chủ API, mà thuộc tính
   * `download` bị trình duyệt bỏ qua khi khác nguồn — bấm vào chỉ mở thêm một
   * tab đang phát nhạc chứ không lưu gì cả. Lấy về thành blob thì tên file do
   * trang đặt, và lời gọi vẫn mang token trong header như mọi lời gọi khác.
   *
   * <p>Không gửi header `Range`, nên máy chủ trả về trọn file trong một lần
   * thay vì từng megabyte một như lúc phát.
   */
  download: (chapterId, audioId) =>
    client
      .get(`/api/chapters/${chapterId}/audio/${audioId}`, { responseType: "blob" })
      .then((r) => r.data),

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
/* Background music                                                    */
/* ------------------------------------------------------------------ */

export const bgmApi = {
  /**
   * The tracks an admin has opened for listeners to choose from.
   *
   * Public, and deliberately so: this is music the site offers on purpose, and
   * the reading page asks for it as soon as a public chapter opens.
   */
  list: (signal) => client.get("/api/bgm", { signal }).then((r) => r.data),

  /**
   * Absolute URL of a track's audio.
   *
   * No token on it, unlike `audioApi.streamUrl`: the endpoint is open, and the
   * mixer fetches this URL directly to decode it into a buffer. Keeping the
   * token out also keeps the URL cacheable — the same music is fetched by
   * every listener, and the server marks it `public, max-age=86400`.
   */
  streamUrl: (path) => new URL(path, API_BASE_URL).toString(),
};

export const ttsApi = {
  /**
   * Whether narration can be requested, and how many goes the caller has left.
   *
   * One call gives the reading page everything it needs to decide between a
   * button, a sign-in prompt and showing nothing at all — rather than offering
   * a button that is bound to fail.
   */
  status: () => client.get("/api/tts/status").then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Trợ lý AI của trang đọc                                             */
/* ------------------------------------------------------------------ */

export const assistantApi = {
  /**
   * Trợ lý có dùng được trên máy chủ này không, và còn bao nhiêu lượt.
   *
   * Hỏi một lần khi mở chương. Máy chủ chưa có API key thì {@code enabled} là
   * false và trang đọc không vẽ gì cả — cùng nếp với {@link ttsApi.status}:
   * một cái nút chắc chắn sẽ lỗi thì đừng mời người ta bấm.
   */
  status: () => client.get("/api/ai/status").then((r) => r.data),

  /**
   * Hỏi trợ lý một câu về chương đang đọc.
   *
   * <p>Chỉ gửi lên `chapterId`, không bao giờ gửi nội dung chương — máy chủ tự
   * tra nội dung sau khi xét quyền đọc. Đây không phải chuyện tiết kiệm băng
   * thông mà là chuyện khoá cửa: nếu nội dung đi từ trình duyệt lên thì bất kỳ
   * ai cũng dán được nội dung một chương chưa mua vào đó.
   *
   * <p>`history` là các lượt đã trao đổi trong đúng chương này, cũ trước mới
   * sau. Máy chủ cắt bớt cho vừa ngân sách token, nên gửi dư không hỏng gì —
   * nhưng cũng không cần gửi cả cuộc.
   */
  ask: ({ chapterId, message, history }) =>
    client
      .post("/api/ai/story-assistant", { chapterId, message, history })
      .then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Reader interaction: favourites, progress, comments                  */
/* ------------------------------------------------------------------ */

export const favoriteApi = {
  status: (storyId) => client.get(`/api/stories/${storyId}/favorite`).then((r) => r.data),
  toggle: (storyId) => client.post(`/api/stories/${storyId}/favorite`).then((r) => r.data),
  mine: (params) => client.get("/api/me/favorites", { params }).then((r) => r.data),
};

export const progressApi = {
  /** `payload` accepts lastPosition (0-100) and audioPositionSeconds. */
  save: (chapterId, payload) =>
    client.put(`/api/chapters/${chapterId}/progress`, payload).then((r) => r.data),

  markRead: (chapterId) => client.post(`/api/chapters/${chapterId}/read`).then((r) => r.data),

  recent: (limit) => client.get("/api/me/reading", { params: { limit } }).then((r) => r.data),

  /** One row per story rather than per chapter, with read/total chapter counts. */
  shelf: () => client.get("/api/me/shelf").then((r) => r.data),

  /**
   * Stories in the genres this reader already reads, minus the ones they have.
   * Empty for anyone with no reading history — there is nothing to go on.
   */
  recommendations: (limit) =>
    client.get("/api/me/recommendations", { params: { limit } }).then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* The signed-in reader's own profile                                  */
/* ------------------------------------------------------------------ */

export const profileApi = {
  /** Returns the updated user, so the auth context can be refreshed from it. */
  uploadAvatar: (file) => {
    const form = new FormData();
    form.append("file", file);
    return client
      .post("/api/me/avatar", form, { headers: { "Content-Type": "multipart/form-data" } })
      .then((r) => r.data);
  },

  removeAvatar: () => client.delete("/api/me/avatar").then((r) => r.data),

  /** False when the server has no Cloudinary keys — the UI then hides the control. */
  avatarAvailable: () => client.get("/api/me/avatar/available").then((r) => r.data),
};

export const commentApi = {
  list: (storyId, params) =>
    client.get(`/api/stories/${storyId}/comments`, { params }).then((r) => r.data),

  create: (storyId, payload) =>
    client.post(`/api/stories/${storyId}/comments`, payload).then((r) => r.data),

  remove: (commentId) => client.delete(`/api/comments/${commentId}`),
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

  /** Every track a chapter has, failed ones included — the reader's list hides those. */
  chapterAudio: (chapterId) =>
    client.get(`/api/admin/chapters/${chapterId}/audio`).then((r) => r.data),

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
  setRole: (id, role) => client.patch(`/api/admin/users/${id}/role`, { role }).then((r) => r.data),

  /** Counts for the console's overview screen. */
  stats: () => client.get("/api/admin/stats").then((r) => r.data),

  /** Series behind the three charts: per-day reads/listens, top stories, lock ratio. */
  charts: (days) => client.get("/api/admin/stats/charts", { params: { days } }).then((r) => r.data),

  /** Every comment across every story; `params` accepts keyword, storyId, page, size. */
  listComments: (params) => client.get("/api/admin/comments", { params }).then((r) => r.data),

  /** `params` accepts storyId, withAudio (true/false/undefined), page and size. */
  listChapterAudio: (params) =>
    client.get("/api/admin/audio/chapters", { params }).then((r) => r.data),

  /** Voices of whichever TTS provider is configured, not tied to a chapter. */
  ttsVoices: () => client.get("/api/admin/audio/voices").then((r) => r.data),

  /** Queues narration for several chapters; answers one row per chapter. */
  batchTts: (payload) => client.post("/api/admin/audio/batch-tts", payload).then((r) => r.data),

  setChapterAccessLevelBulk: (chapterIds, accessLevel) =>
    client.patch("/api/admin/chapters/access-level", { chapterIds, accessLevel }).then((r) => r.data),

  /* ---------------- Nhạc nền ---------------- */

  /** Every track, the switched-off ones included — the reader's list hides those. */
  listBgm: () => client.get("/api/admin/bgm").then((r) => r.data),

  /**
   * Uploads a track. Title and credit ride along in the same multipart as the
   * file: a track only means anything once it has a name, and splitting that
   * into a second call leaves a nameless row behind every time the second half
   * fails.
   */
  uploadBgm: ({ file, title, credit }) => {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    if (credit) form.append("credit", credit);
    return client
      .post("/api/admin/bgm", form, { headers: { "Content-Type": "multipart/form-data" } })
      .then((r) => r.data);
  },

  updateBgm: (id, payload) => client.put(`/api/admin/bgm/${id}`, payload).then((r) => r.data),

  setBgmActive: (id, value) =>
    client.patch(`/api/admin/bgm/${id}/active`, { value }).then((r) => r.data),

  deleteBgm: (id) => client.delete(`/api/admin/bgm/${id}`),

  createGenre: (payload) => client.post("/api/admin/genres", payload).then((r) => r.data),
  updateGenre: (id, payload) => client.put(`/api/admin/genres/${id}`, payload).then((r) => r.data),
  deleteGenre: (id) => client.delete(`/api/admin/genres/${id}`),

  createAuthor: (payload) => client.post("/api/admin/authors", payload).then((r) => r.data),
  updateAuthor: (id, payload) => client.put(`/api/admin/authors/${id}`, payload).then((r) => r.data),
  deleteAuthor: (id) => client.delete(`/api/admin/authors/${id}`),

  /* ---------------- VIP: gói bán ra và đơn hàng ---------------- */

  /** Every plan, including the ones no longer on sale. */
  vipPlans: () => client.get("/api/admin/vip/plans").then((r) => r.data),
  createVipPlan: (payload) => client.post("/api/admin/vip/plans", payload).then((r) => r.data),
  updateVipPlan: (id, payload) =>
    client.put(`/api/admin/vip/plans/${id}`, payload).then((r) => r.data),
  setVipPlanActive: (id, value) =>
    client.patch(`/api/admin/vip/plans/${id}/active`, { value }).then((r) => r.data),

  /** `params` accepts status, page and size. */
  vipOrders: (params) => client.get("/api/admin/vip/orders", { params }).then((r) => r.data),

  /** Asks the gateway what really happened to an order still sitting pending. */
  refreshVipOrder: (orderCode) =>
    client.post(`/api/admin/vip/orders/${orderCode}/refresh`).then((r) => r.data),

  /* ---------------- Xu: gói nạp, giá chương, điều chỉnh số dư ---------------- */

  /** Every package, including the ones no longer on sale. */
  coinPackages: () => client.get("/api/admin/wallet/packages").then((r) => r.data),

  createCoinPackage: (payload) =>
    client.post("/api/admin/wallet/packages", payload).then((r) => r.data),

  updateCoinPackage: (id, payload) =>
    client.put(`/api/admin/wallet/packages/${id}`, payload).then((r) => r.data),

  setCoinPackageActive: (id, active) =>
    client.patch(`/api/admin/wallet/packages/${id}/active`, { active }).then((r) => r.data),

  /** Giá mở khóa một chương. 0 nghĩa là không bán lẻ. */
  setChapterPrice: (id, coinPrice) =>
    client.patch(`/api/admin/chapters/${id}/pricing`, { coinPrice }).then((r) => r.data),

  setChapterPriceBulk: (chapterIds, coinPrice) =>
    client.patch("/api/admin/chapters/pricing", { chapterIds, coinPrice }).then((r) => r.data),

  /** `amount` có dấu: dương là cộng, âm là trừ. Lý do đi vào sổ cái người dùng đọc được. */
  adjustWallet: (userId, amount, reason) =>
    client.post(`/api/admin/wallet/users/${userId}/adjust`, { amount, reason }).then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* Ví Xu (reader side)                                                 */
/* ------------------------------------------------------------------ */

export const walletApi = {
  /** Số dư hiện tại. Người chưa từng nạp có số dư 0, không phải lỗi. */
  balance: () => client.get("/api/wallet").then((r) => r.data),

  /** `params` nhận page và size. Mới nhất trước. */
  transactions: (params) =>
    client.get("/api/wallet/transactions", { params }).then((r) => r.data),

  /** Công khai, cùng lý do với bảng giá VIP: giá là thứ người ta xem trước khi đăng ký. */
  packages: () => client.get("/api/wallet/packages").then((r) => r.data),

  /**
   * Tạo đơn nạp Xu; `checkoutUrl` trong kết quả là nơi trình duyệt đi tiếp.
   *
   * Trả về cùng hình dạng đơn với `vipApi.createOrder`, nên trang kết quả thanh
   * toán dùng chung được cho cả hai — nó chỉ cần đọc `kind` để biết vừa bán gì.
   */
  createOrder: (packageId) =>
    client.post("/api/wallet/orders", { packageId }).then((r) => r.data),
};

/* ------------------------------------------------------------------ */
/* VIP (reader side)                                                   */
/* ------------------------------------------------------------------ */

export const vipApi = {
  /** Public: the price list is what someone reads before deciding to sign up. */
  plans: () => client.get("/api/vip/plans").then((r) => r.data),

  status: () => client.get("/api/vip/me").then((r) => r.data),

  orders: () => client.get("/api/vip/orders").then((r) => r.data),

  /** Answers with the order, whose `checkoutUrl` is where the browser goes next. */
  createOrder: (planId) => client.post("/api/vip/orders", { planId }).then((r) => r.data),

  /** Re-checks one order; a pending one is verified against the gateway. */
  checkOrder: (orderCode) => client.get(`/api/vip/orders/${orderCode}`).then((r) => r.data),

  cancelOrder: (orderCode) =>
    client.post(`/api/vip/orders/${orderCode}/cancel`).then((r) => r.data),
};
