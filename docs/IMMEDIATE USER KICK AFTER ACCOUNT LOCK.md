````md
# TASK — IMMEDIATE USER KICK AFTER ACCOUNT LOCK

## 1. ROLE

Bạn hãy đóng vai:

- Senior Software Architect
- Senior Backend Engineer
- Senior Security Engineer
- Senior Full-Stack Engineer

Mục tiêu của task này là khắc phục triệt để lỗi:

> Admin khóa tài khoản của một user trong khi user đó vẫn đang đăng nhập và sử dụng website.
>
> Hiện tại user không bị kick ngay. User vẫn có thể tiếp tục đọc truyện, đọc truyện VIP, xem profile, click các chức năng khác và thực hiện các request dù tài khoản đã bị Admin khóa.

Đây là một vấn đề liên quan đến:

- Authentication
- Authorization
- Session invalidation
- Account state
- Security
- Frontend authentication state
- Backend request interception
- Race condition
- VIP/Xu authorization
- Real-time account status
- Multi-tab session handling

Phải xử lý ở BACKEND trước.

Frontend chỉ là lớp UX bổ sung.

---

# 2. BUSINESS REQUIREMENT

Khi Admin thực hiện:

```text
Admin
  ↓
Lock User Account
````

thì user đó phải ngay lập tức trở thành:

```text
ACCOUNT_LOCKED
```

Từ thời điểm account lock có hiệu lực:

```text
User MUST NOT be able to perform authenticated operations.
```

Điều này áp dụng cho:

* đọc truyện
* đọc truyện VIP
* mở chapter bằng Xu
* xem profile
* chỉnh sửa profile
* đổi password
* generate TTS
* sử dụng AI Assistant
* gọi API
* gửi comment
* like
* bookmark
* history
* follow
* upload
* payment-related authenticated operations
* mọi API yêu cầu authentication
* mọi chức năng authenticated khác

Không được chỉ chặn một vài API.

---

# 3. IMPORTANT SECURITY PRINCIPLE

Frontend KHÔNG phải source of truth cho account status.

Không được giải quyết chỉ bằng:

```text
if (user.isLocked) {
    logout();
}
```

vì user có thể:

* gọi API trực tiếp
* mở DevTools
* sử dụng một tab khác
* giữ access token cũ
* giữ refresh token cũ
* gọi API bằng Postman
* replay request

Backend phải tự kiểm tra account state.

Invariant:

```text
LOCKED USER
    ↓
NO AUTHENTICATED REQUEST MAY SUCCEED
```

---

# 4. AUDIT BEFORE CODING

KHÔNG code ngay.

Trước tiên audit toàn bộ authentication/authorization architecture hiện tại.

Tìm:

## User

* User entity
* User repository
* User service
* User status
* enabled/disabled/locked fields
* account state
* deleted state
* banned state

## Authentication

* Login API
* Logout API
* JWT
* Access token
* Refresh token
* Session
* Cookie
* SecurityContext
* Spring Security
* AuthenticationFilter
* OncePerRequestFilter
* AuthenticationProvider
* UserDetailsService
* Token validation

## Authorization

* Roles
* Permissions
* VIP checks
* Xu checks
* Chapter unlock
* Ownership/entitlement checks

## Admin

* Admin lock user API
* Admin unlock user API
* User management
* Account status update

## Frontend

* Auth store
* User store
* JWT storage
* Cookie handling
* Axios/fetch interceptor
* React/Vue/etc route guards
* logout flow
* global error handling

## Realtime

Kiểm tra project hiện tại có:

* WebSocket
* SSE
* polling
* push mechanism

hay không.

## API

Tìm tất cả authenticated endpoints.

Không chỉ tìm endpoint liên quan đến profile.

---

# 5. CURRENT ARCHITECTURE REPORT

Trước implementation phải báo cáo:

## A. Authentication architecture

## B. Token/session architecture

## C. User account status architecture

## D. Admin lock flow

## E. Backend security filter flow

## F. Frontend auth flow

## G. Token storage

## H. Refresh token mechanism

## I. VIP/Xu authorization flow

## J. Current chapter access flow

## K. Current logout flow

## L. Multi-tab behavior

## M. Current realtime capability

## N. Identified security gaps

## O. Files that need modification

Không được tự ý thay đổi architecture lớn nếu chưa giải thích lý do.

---

# 6. DEFINE ACCOUNT STATES

Audit trạng thái user hiện tại.

Nếu project chưa có clear account state, đề xuất trạng thái phù hợp.

Ví dụ:

```text
ACTIVE
LOCKED
```

Nếu project đã có:

```text
ACTIVE
BANNED
DISABLED
DELETED
```

thì phải reuse architecture hiện tại.

Không tự ý tạo nhiều trạng thái mới nếu không cần.

---

# 7. ADMIN LOCK MUST BE AUTHORITATIVE

Khi Admin lock user:

```text
Admin
 ↓
Lock User
 ↓
Database transaction
 ↓
user.status = LOCKED
```

Database phải là source of truth.

Không chỉ thay đổi:

```text
Frontend admin UI
```

hoặc:

```text
Redis flag
```

mà không cập nhật persistent account state.

---

# 8. TOKEN INVALIDATION

Đây là phần cực kỳ quan trọng.

Audit token architecture hiện tại.

Nếu dùng JWT stateless:

```text
JWT
```

thường token vẫn còn valid cho tới khi expire.

KHÔNG được chấp nhận behavior:

```text
Admin locks user
        ↓
Old JWT remains valid
        ↓
User continues API requests
```

Phải thiết kế cơ chế kiểm tra account status hoặc token/session version.

Một hướng production hợp lý:

```text
User
----------------
id
accountStatus
tokenVersion
```

Khi Admin lock:

```text
accountStatus = LOCKED
tokenVersion++
```

Authentication layer phải đảm bảo token/session cũ không thể tiếp tục sử dụng.

Tuy nhiên, nếu project đã có một cơ chế session/token revocation phù hợp thì reuse cơ chế hiện tại.

Không tạo duplicate mechanism nếu không cần.

---

# 9. BACKEND GLOBAL ACCOUNT STATUS CHECK

Đây là requirement BẮT BUỘC.

Mọi authenticated request phải đi qua một lớp kiểm tra account status.

Flow:

```text
HTTP Request
    ↓
Authenticate Token/Session
    ↓
Resolve User
    ↓
Check Account Status
    ↓
ACTIVE?
   /   \
 YES    NO
  ↓      ↓
Continue Reject
         ↓
      401/403
         ↓
   ACCOUNT_LOCKED
```

Không được chỉ check tại:

```text
/user/profile
```

hoặc:

```text
/chapter/vip
```

Mà phải là global security layer.

Nếu dùng Spring Security, ưu tiên xử lý ở:

* filter
* authentication provider
* user details validation
* authorization layer phù hợp

tùy architecture hiện tại.

---

# 10. LOCKED USER RESPONSE

Khi backend phát hiện:

```text
accountStatus = LOCKED
```

phải trả response có thể phân biệt được với:

* normal 401
* expired token
* invalid token
* forbidden resource

Ví dụ:

```http
HTTP 401
```

hoặc status code phù hợp với architecture hiện tại.

Response có machine-readable error code:

```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Your account has been locked."
}
```

Không bắt frontend phải parse message text để xác định trạng thái.

Frontend phải dựa vào:

```text
code = ACCOUNT_LOCKED
```

---

# 11. FRONTEND GLOBAL INTERCEPTOR

Frontend phải có một global authentication response handler.

Ví dụ concept:

```text
API Request
    ↓
Backend
    ↓
ACCOUNT_LOCKED
    ↓
Global API Interceptor
    ↓
Clear authentication state
    ↓
Clear token/session
    ↓
Redirect to homepage
```

Không implement riêng từng page.

Không viết:

```text
ProfilePage:
if locked → logout

ReaderPage:
if locked → logout

VipPage:
if locked → logout
```

Đây là bad architecture.

Phải có một global mechanism.

---

# 12. USER MUST BE KICKED ON NEXT INTERACTION

Business requirement chính xác:

> Khi Admin khóa user, nếu user đang mở website thì user phải bị kick ngay khi hệ thống phát hiện account đã bị khóa.

User có thể đang:

* đọc chapter
* nghe audio
* đứng ở homepage
* mở profile
* mở VIP page
* mở Xu page
* mở AI Assistant
* không làm gì
* mở nhiều tab

Nếu không có realtime mechanism:

thì ít nhất request/interaction tiếp theo phải detect:

```text
ACCOUNT_LOCKED
```

và lập tức:

```text
logout
+
redirect /
```

Không được để user tiếp tục sử dụng thêm một authenticated operation nào sau khi backend đã biết account bị khóa.

---

# 13. CLICK / TOUCH REQUIREMENT

User yêu cầu đặc biệt:

> kick user ngay lập tức khi có bất kỳ thao tác click/chạm gì sau khi bị Admin khóa.

Nếu architecture hiện tại chưa có realtime push, hãy thiết kế một global interaction guard phù hợp.

Ví dụ concept:

```text
User is authenticated
        ↓
Admin locks account
        ↓
User clicks/touches anything
        ↓
Frontend performs account-state validation
        ↓
ACCOUNT_LOCKED
        ↓
Logout
        ↓
Redirect "/"
```

Tuy nhiên:

KHÔNG được tạo một API request cho từng pixel/mouse event.

Không:

```text
mousemove
touchmove
scroll
```

đều gọi API.

Chỉ xử lý những interaction có ý nghĩa hoặc một global mechanism phù hợp.

---

# 14. PREFERRED REALTIME APPROACH

Nếu project đã có:

```text
WebSocket
```

hoặc:

```text
SSE
```

hãy ưu tiên sử dụng nó.

Flow:

```text
Admin locks user
        ↓
Backend
        ↓
Account status updated
        ↓
Push ACCOUNT_LOCKED event
        ↓
User browser
        ↓
Global auth handler
        ↓
Logout
        ↓
Redirect "/"
```

Event:

```json
{
  "type": "ACCOUNT_LOCKED",
  "userId": "..."
}
```

Frontend không cần chờ click.

Có thể kick gần realtime.

---

# 15. IF NO REALTIME SYSTEM EXISTS

Nếu project chưa có WebSocket/SSE:

Không tự ý xây một hệ thống realtime phức tạp chỉ để giải quyết task này.

Thiết kế fallback:

```text
User interaction
        ↓
Authenticated request
        ↓
Backend account status check
        ↓
ACCOUNT_LOCKED
        ↓
Global interceptor
        ↓
Logout
        ↓
Redirect "/"
```

Có thể cân nhắc polling nhẹ nếu business thực sự yêu cầu "kick ngay cả khi user không gửi request".

Nhưng không polling quá aggressive.

Không tạo:

```text
API call every 100ms
```

---

# 16. IMPORTANT DISTINCTION

Phải phân biệt:

## Case A — User đang thao tác

```text
Admin locks user
        ↓
User clicks
        ↓
API request
        ↓
ACCOUNT_LOCKED
        ↓
Kick
```

## Case B — User không thao tác

```text
Admin locks user
        ↓
User chỉ ngồi yên
```

Nếu không có realtime:

không thể biết browser đã bị khóa nếu browser không gửi request.

Nếu business yêu cầu kick thực sự realtime trong Case B:

phải sử dụng:

* WebSocket
* SSE
* hoặc polling hợp lý

Không được giả vờ rằng frontend có thể tự biết database đã thay đổi mà không có communication channel.

---

# 17. REDIRECT REQUIREMENT

Sau khi account bị lock:

Frontend phải:

```text
clear auth state
clear access token
clear refresh token/session if applicable
stop authenticated background activity
redirect "/"
```

Redirect về:

```text
/
```

Homepage.

Không redirect về:

```text
/login
```

trừ khi architecture/business requirement hiện tại yêu cầu.

User có thể nhìn thấy thông báo:

```text
Tài khoản của bạn đã bị khóa.
```

nhưng không được tiếp tục truy cập protected pages.

---

# 18. STOP BACKGROUND OPERATIONS

Sau khi detect:

```text
ACCOUNT_LOCKED
```

phải dừng:

* TTS generation requests
* AI Assistant requests
* polling
* chapter auto-save
* history update
* heartbeat
* notification subscription nếu cần
* background API requests
* audio generation requests

Không để frontend tiếp tục spam authenticated API sau khi logout.

---

# 19. AUDIO PLAYER

Nếu user đang nghe audio khi account bị lock:

Frontend phải:

```text
stop audio
```

và:

```text
logout
+
redirect "/"
```

Không để user tiếp tục sử dụng authenticated content sau khi account đã bị khóa.

Đặc biệt kiểm tra:

* VIP audio
* premium chapter
* protected audio URL
* signed URL
* cached audio

---

# 20. PREMIUM CONTENT SECURITY

Đặc biệt audit:

```text
GET /vip/chapter
GET /audio
GET /chapter
GET /profile
```

và mọi API protected.

Một user bị khóa không được:

```text
continue accessing VIP content
```

thông qua:

* cached frontend data
* old API response
* old JWT
* direct API call
* old audio URL
* old signed URL nếu hệ thống đang dùng signed URL

---

# 21. SIGNED AUDIO URL

Nếu audio sử dụng:

* signed URL
* presigned URL
* CDN URL

phải audit kỹ.

Ví dụ:

```text
User gets signed URL
        ↓
Admin locks user
        ↓
URL remains valid
```

Nếu business/security requirement yêu cầu revoke ngay cả URL đã cấp:

phải đánh giá khả năng:

* short expiration
* backend proxy
* CDN authorization
* token validation
* revocation strategy

Không giả định rằng revoke JWT tự động revoke một URL đã ký.

Nếu signed URL chỉ sống trong thời gian ngắn, hãy báo cáo trade-off.

---

# 22. REFRESH TOKEN

Nếu hệ thống có refresh token:

BẮT BUỘC xử lý.

Case:

```text
Access Token expired
        ↓
Frontend uses Refresh Token
        ↓
Admin already locked account
```

Refresh phải bị reject:

```text
ACCOUNT_LOCKED
```

Không được cấp access token mới.

---

# 23. LOGIN AFTER LOCK

User bị lock:

```text
logout
```

Sau đó cố login:

```text
POST /login
```

phải bị từ chối.

Expected:

```json
{
  "code": "ACCOUNT_LOCKED"
}
```

Không được login thành công bằng password đúng.

---

# 24. UNLOCK FLOW

Audit Admin unlock:

```text
LOCKED
 ↓
ADMIN UNLOCK
 ↓
ACTIVE
```

Nếu dùng:

```text
tokenVersion
```

phải xác định behavior:

* token cũ có được dùng lại không?
* user có phải login lại không?

Khuyến nghị security:

```text
LOCK
 ↓
invalidate existing sessions
 ↓
UNLOCK
 ↓
require fresh login
```

Nhưng phải phù hợp với architecture hiện tại.

Không tự ý làm user active lại với token cũ nếu security model không cho phép.

---

# 25. MULTI-TAB

Bắt buộc test:

```text
Tab A → Reader
Tab B → Profile
Tab C → VIP
```

Admin:

```text
Lock User
```

Expected:

```text
Tab A → logout
Tab B → logout
Tab C → logout
```

Nếu không có realtime:

tab nào có interaction/request tiếp theo phải bị kick ngay.

Nếu có realtime:

tất cả tab phải nhận event.

Frontend auth state phải được đồng bộ qua:

* BroadcastChannel
* storage event
* shared auth store
* hoặc mechanism hiện tại

nếu phù hợp.

---

# 26. BACKGROUND REQUEST RACE CONDITION

Phải xử lý case:

```text
Request A → started before lock
Admin → lock user
Request A → completes after lock
```

Không nên để frontend tiếp tục coi user authenticated chỉ vì Request A trả về 200.

Sau khi global lock event/error đã được nhận:

```text
ACCOUNT_LOCKED
```

phải có auth state transition:

```text
AUTHENTICATED
      ↓
LOCKED
      ↓
LOGGED_OUT
```

Late responses không được khôi phục auth state.

---

# 27. ADMIN LOCK TRANSACTION

Admin lock operation phải đảm bảo:

```text
accountStatus = LOCKED
```

được commit thành công trước khi báo Admin:

```text
User locked successfully
```

Không hiển thị success nếu database update thất bại.

Nếu có event push:

event phải được gửi sau khi state đã persist thành công.

---

# 28. TRANSACTION BOUNDARY

Admin lock transaction phải ngắn.

Không:

```text
@Transactional
lock user
call external service
wait
send notification
...
commit
```

Ưu tiên:

```text
DB transaction
    ↓
update user status
    ↓
commit
    ↓
publish notification/event
```

Không giữ DB transaction trong lúc chờ external systems.

---

# 29. CACHE INVALIDATION

Audit:

* Redis
* Spring Cache
* User cache
* Security cache
* Session cache

Nếu user account status đang cached:

phải đảm bảo lock event invalidates hoặc bypasses stale cache.

Không được có:

```text
Database:
LOCKED

Cache:
ACTIVE
```

và authentication layer tin cache cũ.

Nếu dùng distributed cache, phân tích consistency requirement.

---

# 30. SECURITY RESPONSE CONSISTENCY

Mọi protected API phải có behavior nhất quán.

Ví dụ:

```text
GET /profile
GET /chapters
GET /vip
POST /tts
POST /ai
POST /comment
POST /purchase
```

đều phải trả cùng machine-readable error:

```text
ACCOUNT_LOCKED
```

Không:

```text
/profile → 403
/vip → 500
/tts → 200
/ai → 401
```

vì account lock phải là global authentication state.

---

# 31. DO NOT TRUST FRONTEND USER OBJECT

Không được chỉ dựa vào:

```javascript
currentUser.status
```

vì:

```text
currentUser.status = ACTIVE
```

có thể là stale.

Backend database/security layer là source of truth.

Frontend chỉ phản ứng theo server.

---

# 32. CACHE / LOCAL STORAGE

Audit:

```text
localStorage
sessionStorage
IndexedDB
cookies
```

Nếu auth state/token được lưu ở đó:

khi logout do ACCOUNT_LOCKED phải clear đúng dữ liệu.

Không để:

```text
refresh
```

và user trở lại trạng thái authenticated.

---

# 33. ERROR HANDLING

Frontend phải có global handler:

```text
if response.code === "ACCOUNT_LOCKED":
    terminateSession()
    redirect("/")
```

Phải đảm bảo:

* chỉ chạy một lần
* không tạo redirect loop
* không gọi logout API vô hạn
* không retry request vô hạn
* không refresh token sau ACCOUNT_LOCKED

Đặc biệt:

```text
ACCOUNT_LOCKED
```

không được đưa vào generic:

```text
retry request
```

logic.

---

# 34. API RETRY

Nếu frontend có:

* axios retry
* fetch retry
* React Query retry
* TanStack Query retry
* SWR retry

phải disable retry đối với:

```text
ACCOUNT_LOCKED
```

Không được:

```text
401
 ↓
retry
 ↓
401
 ↓
retry
```

hoặc:

```text
ACCOUNT_LOCKED
 ↓
refresh token
 ↓
retry
```

---

# 35. TEST CASES

Bắt buộc test:

## Test 1 — Reader

```text
User đọc truyện
Admin lock
User click chapter
```

Expected:

```text
Kick → /
```

## Test 2 — VIP

```text
User đang VIP
Admin lock
User mở VIP chapter
```

Expected:

```text
Kick
```

## Test 3 — Profile

```text
User mở profile
Admin lock
User click profile action
```

Expected:

```text
Kick
```

## Test 4 — Xu

```text
Admin lock
User attempt purchase/use Xu
```

Expected:

```text
Kick
```

## Test 5 — TTS

```text
Admin lock
User click TTS
```

Expected:

```text
Kick
```

## Test 6 — AI

```text
Admin lock
User click AI Assistant
```

Expected:

```text
Kick
```

## Test 7 — Direct API

Dùng Postman/curl hoặc integration test:

```text
Old valid token
+
locked account
```

Expected:

```text
ACCOUNT_LOCKED
```

## Test 8 — Refresh Token

```text
Account locked
+
refresh token
```

Expected:

```text
ACCOUNT_LOCKED
```

## Test 9 — Multi-tab

Tất cả tab phải logout.

## Test 10 — Reload

Sau khi logout:

```text
reload
```

không được authenticated.

## Test 11 — Login

Locked user không login được.

## Test 12 — Race condition

Request đang chạy trong lúc Admin lock.

Late response không được khôi phục authenticated state.

---

# 36. ACCEPTANCE TEST — MAIN SCENARIO

Bắt buộc pass scenario:

```text
1. Login bằng User A.

2. Mở Reader.

3. Mở VIP chapter.

4. Giữ trang đang mở.

5. Admin đăng nhập bằng Admin account.

6. Admin lock User A.

7. User A vẫn đang ở Reader.

8. User A click bất kỳ authenticated action nào.

9. Request gửi lên backend.

10. Backend kiểm tra account status.

11. Backend phát hiện:

    accountStatus = LOCKED

12. Backend trả:

    ACCOUNT_LOCKED

13. Frontend global interceptor nhận response.

14. Frontend:
    - stop audio
    - cancel authenticated background operations
    - clear auth state
    - clear token/session
    - stop refresh-token flow
    - redirect "/"

15. User không thể tiếp tục:
    - đọc VIP
    - xem profile
    - sử dụng Xu
    - TTS
    - AI
    - comment
    - like
    - bookmark
    - hoặc bất kỳ authenticated feature nào.

16. User reload.

17. User vẫn ở trạng thái logged out.

18. User cố login lại.

19. Backend trả:

    ACCOUNT_LOCKED
```

---

# 37. REALTIME ACCEPTANCE TEST

Nếu project triển khai SSE/WebSocket:

```text
1. User đang đứng yên trên Reader.

2. Admin lock account.

3. Không cần user click.

4. Browser nhận:

   ACCOUNT_LOCKED

5. Frontend lập tức:

   stop audio
   clear session
   redirect "/"
```

Nếu project không có realtime:

phải document rõ:

```text
The browser cannot know about a database-side account lock
without a communication channel.

Therefore the next authenticated interaction/request
must trigger the backend validation.
```

Không được tuyên bố "instant kick" nếu architecture không có realtime communication.

---

# 38. PERFORMANCE REQUIREMENT

Account status validation phải có overhead hợp lý.

Không được:

```text
Every API request
    ↓
Heavy database query
```

nếu có giải pháp tốt hơn trong architecture hiện tại.

Đánh giá:

* JWT claims
* tokenVersion
* cache
* session store
* DB lookup
* Redis

Nhưng:

SECURITY > PERFORMANCE.

Không cache account status quá lâu đến mức user bị lock nhưng vẫn có thể truy cập protected resources.

Nếu dùng cache, phải có invalidation khi Admin lock.

---

# 39. DO NOT OVERENGINEER

Đây là production cá nhân.

Không tự ý thêm:

* Kafka
* RabbitMQ
* Kubernetes
* microservices
* distributed event sourcing
* complex identity provider

chỉ để giải quyết account lock.

Ưu tiên:

```text
Existing Authentication
+
Global Backend Account Check
+
Global Frontend Interceptor
+
Token/Session Invalidation
+
SSE/WebSocket if already available or genuinely required
```

---

# 40. FINAL SECURITY INVARIANTS

Implementation phải đảm bảo:

### Invariant 1

```text
LOCKED USER
    ↓
NO PROTECTED API ACCESS
```

### Invariant 2

```text
LOCKED USER
    ↓
NO VIP ACCESS
```

### Invariant 3

```text
LOCKED USER
    ↓
NO XU OPERATIONS
```

### Invariant 4

```text
LOCKED USER
    ↓
NO TTS
```

### Invariant 5

```text
LOCKED USER
    ↓
NO AI ASSISTANT
```

### Invariant 6

```text
LOCKED USER
    ↓
NO PROFILE OPERATIONS
```

### Invariant 7

```text
LOCKED USER
    ↓
NO REFRESH TOKEN
```

### Invariant 8

```text
LOCKED USER
    ↓
NO LOGIN
```

### Invariant 9

```text
ACCOUNT_LOCKED
    ↓
FRONTEND LOGOUT
    ↓
REDIRECT /
```

### Invariant 10

```text
FRONTEND AUTH STATE
MUST NEVER OVERRIDE
BACKEND ACCOUNT STATE
```

---

# 41. FINAL IMPLEMENTATION REPORT

Sau khi hoàn thành, Agent phải báo cáo:

## 1. Current Authentication Architecture

## 2. Root Cause

Giải thích chính xác tại sao user hiện tại không bị kick sau khi Admin lock.

## 3. Backend Changes

Liệt kê:

* filters
* services
* repositories
* entities
* controllers
* security configuration

## 4. Database Changes

Nếu có:

* migration
* new fields
* indexes
* constraints

## 5. Token/Session Changes

Mô tả cách invalidate session/token.

## 6. Frontend Changes

Liệt kê:

* interceptor
* auth store
* route guard
* logout logic
* redirect logic

## 7. Realtime Changes

Nếu có:

* SSE
* WebSocket
* event

## 8. VIP/Xu Security

Chứng minh locked user không thể bypass.

## 9. Audio Security

Chứng minh locked user không thể tiếp tục sử dụng protected audio ngoài policy hiện tại.

## 10. Race Conditions

Mô tả cách xử lý.

## 11. Multi-tab

Mô tả behavior.

## 12. Tests

Liệt kê toàn bộ tests đã chạy.

## 13. Remaining Risks

Nêu rõ các rủi ro còn lại.

---

# 42. IMPORTANT AGENT RULES

1. Audit trước khi code.
2. Không chỉ sửa frontend.
3. Backend phải là source of truth.
4. Không chỉ kiểm tra account status ở một vài endpoint.
5. Dùng global authentication/security layer.
6. Không để locked user refresh token.
7. Không retry ACCOUNT_LOCKED.
8. Không bypass bằng VIP/Xu/audio API.
9. Không tin user object ở frontend.
10. Không tạo API polling quá aggressive.
11. Không tự ý thêm infrastructure phức tạp.
12. Không phá authentication hiện tại.
13. Không phá VIP/Xu.
14. Không phá TTS.
15. Không phá AI Assistant.
16. Không phá Reader.
17. Không tạo redirect loop.
18. Không để late API response khôi phục session.
19. Không coi JWT cũ là hợp lệ chỉ vì chữ ký vẫn valid nếu account đã LOCKED.
20. Nếu architecture hiện tại khác với assumptions trong task này, phải báo cáo trước khi thay đổi.

---

# FINAL GOAL

Sau implementation:

```text
                    ADMIN
                      │
                      │ LOCK USER
                      ▼
              ┌───────────────┐
              │ accountStatus │
              │    = LOCKED  │
              └───────┬───────┘
                      │
              ┌───────┴────────┐
              │                │
              ▼                ▼
        Backend Security   Realtime Event
              │                │
              │                ▼
              │          User Browser
              │                │
              ▼                ▼
       Reject Request      Logout
              │                │
              ▼                ▼
    ACCOUNT_LOCKED        Redirect "/"
```

Mục tiêu cuối cùng:

```text
ADMIN LOCKS USER
       ↓
USER SESSION BECOMES INVALID
       ↓
NO PROTECTED OPERATION CAN SUCCEED
       ↓
USER IS LOGGED OUT
       ↓
USER IS REDIRECTED TO HOMEPAGE
```

Không được tồn tại trạng thái:

```text
ACCOUNT = LOCKED
+
USER = STILL AUTHENTICATED
+
USER CAN ACCESS VIP / PROFILE / XU / TTS / AI / READER
```

Đây là security invariant bắt buộc phải được đảm bảo sau implementation.

```
```
