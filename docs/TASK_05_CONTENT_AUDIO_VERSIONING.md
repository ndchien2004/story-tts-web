````md
# TASK 05 — CONTENT & AUDIO VERSIONING

## 1. ROLE

Bạn hãy đóng vai:

- Senior Software Architect
- Senior Backend Engineer
- Senior Full-Stack Engineer
- Database Engineer
- Concurrency / Distributed Systems Engineer

Mục tiêu của task này là giải quyết triệt để vấn đề:

> User đang đọc một chapter và đã yêu cầu hệ thống generate audio bằng AI/TTS.
> Trong lúc TTS đang generate hoặc sau khi audio đã được tạo, Admin chỉnh sửa nội dung chapter.
> User không được thông báo rằng chapter đã thay đổi.
> Audio cũ vẫn có thể được phát dù nội dung chapter hiện tại đã thay đổi.
> Ngay cả khi user reload trang, hệ thống vẫn có thể trả về audio cũ.

Đây là vấn đề về:

- Data consistency
- Content versioning
- Audio versioning
- Async processing
- Race condition
- Cache invalidation
- TTS lifecycle
- Client synchronization
- Database consistency

KHÔNG được giải quyết đơn giản bằng:

- reload page
- clear browser cache
- đổi filename ngẫu nhiên
- overwrite audio file
- timestamp URL
- frontend validation
- cache busting

Phải giải quyết từ domain/business logic và backend trước.

---

# 2. BUSINESS RULE QUAN TRỌNG NHẤT

Một audio chỉ được coi là hợp lệ nếu nó được generate từ ĐÚNG phiên bản content hiện tại của chapter.

Ví dụ:

Chapter 10:

```text
contentVersion = 1
````

Audio:

```text
chapterId = 10
contentVersion = 1
status = READY
```

=> Audio hợp lệ.

Sau đó Admin sửa nội dung chapter:

```text
contentVersion: 1 → 2
```

Audio cũ:

```text
chapterId = 10
contentVersion = 1
```

=> Audio này trở thành STALE.

Hệ thống KHÔNG được tiếp tục coi audio version 1 là audio hiện tại của chapter version 2.

Business invariant bắt buộc:

```text
CURRENT_AUDIO.contentVersion
==
CURRENT_CHAPTER.contentVersion
```

Nếu không bằng nhau:

```text
CURRENT_AUDIO MUST NOT BE SERVED AS CURRENT AUDIO
```

Đây là source of truth của toàn bộ implementation.

---

# 3. AUDIT BEFORE CODING

KHÔNG code ngay.

Trước tiên hãy audit toàn bộ codebase hiện tại.

Tìm và phân tích:

## Chapter

* Chapter entity
* Chapter DTO
* Chapter repository
* Chapter service
* Chapter controller
* Chapter update flow
* Admin chapter update API
* Các field liên quan đến `updatedAt`, version hoặc revision

## Audio

* Audio entity
* Audio metadata
* Audio repository
* Audio service
* Audio controller
* Audio URL/path
* Audio storage
* Audio generation logic
* Audio player API

## TTS

Tìm:

* Gemini
* ElevenLabs
* Google TTS
* các TTS provider khác
* async processing
* `@Async`
* `CompletableFuture`
* scheduled job
* background worker
* queue nếu có

## Database

Kiểm tra:

* Chapter table
* Audio table
* Foreign keys
* Unique constraints
* Existing version fields
* `updated_at`
* Optimistic locking
* Migration mechanism

## Frontend

Tìm:

* Reader page
* Chapter loading
* Audio player
* TTS button
* Audio URL loading
* State management
* Bottom bar
* Loading/error state

## Admin

Tìm:

* Chapter editing UI
* Chapter update API
* Save flow

## Cache

Tìm:

* Redis
* Spring Cache
* CDN
* browser cache
* localStorage
* sessionStorage
* HTTP cache

## Transaction

Đặc biệt audit:

* `@Transactional`
* EntityManager
* repository calls
* lazy loading
* DB connection lifecycle

Mục tiêu là xác định chính xác external TTS API hiện đang được gọi ở đâu và có nằm bên trong DB transaction hay không.

---

# 4. CURRENT ARCHITECTURE REPORT

Trước khi implementation, hãy báo cáo:

## A. Current Architecture

## B. Current Chapter Flow

## C. Current Admin Update Flow

## D. Current TTS Flow

## E. Current Audio Storage

## F. Current Audio Resolution

## G. Current Transaction Boundaries

## H. Current Cache Strategy

## I. Current Frontend Audio Player

## J. Current Authorization Flow

## K. Identified Race Conditions

## L. Identified Data Consistency Problems

## M. Files That Need To Change

Không được tự ý code trước khi hoàn thành audit.

---

# 5. CHAPTER CONTENT VERSIONING

Đề xuất thêm hoặc sử dụng cơ chế:

```text
chapter.contentVersion
```

Ví dụ:

```text
Chapter
-------------------------
id
title
content
contentVersion
updatedAt
```

Khi ADMIN thay đổi nội dung chapter:

```text
contentVersion += 1
```

Ví dụ:

```text
Version 1
    ↓
Admin edits content
    ↓
Version 2
```

## Quan trọng

Chỉ tăng `contentVersion` khi CONTENT thực sự thay đổi.

Không nhất thiết tăng version khi chỉ thay đổi:

* view count
* like count
* comment count
* metadata không ảnh hưởng đến nội dung đọc
* audio metadata

Phải xác định rõ trong code đâu là "content change".

---

# 6. ATOMIC CHAPTER UPDATE

Khi Admin sửa chapter:

```text
content update
+
contentVersion increment
```

phải được thực hiện atomically.

Không được xảy ra:

```text
new content + old version
```

hoặc:

```text
old content + new version
```

Ví dụ:

```text
Before:
contentVersion = 7

After:
content = NEW_CONTENT
contentVersion = 8
```

Hai thay đổi này phải nhất quán trong cùng một database transaction ngắn.

---

# 7. OPTIMISTIC LOCKING

Kiểm tra Chapter entity hiện tại có sử dụng optimistic locking hay không.

Ví dụ:

```java
@Version
```

Nếu project đã có cơ chế tương đương thì reuse.

Nếu chưa có, hãy đánh giá có cần bổ sung hay không để tránh:

```text
Admin A edits chapter
Admin B edits chapter
        ↓
One update accidentally overwrites another
```

Không tự ý thêm nếu không cần thiết.

Nếu đề xuất thêm optimistic locking:

* giải thích lý do
* ảnh hưởng database
* ảnh hưởng API
* cách xử lý conflict

---

# 8. AUDIO VERSIONING

Audio phải lưu version của chapter content mà nó được generate từ.

Ví dụ:

```text
Audio
-------------------------
id
chapterId
contentVersion
storageKey
status
createdAt
updatedAt
```

Có thể bổ sung:

```text
contentHash
```

nếu thực sự cần.

Không được tự ý thêm quá nhiều field nếu architecture hiện tại không cần.

---

# 9. AUDIO STATUS

Audit trạng thái audio hiện tại.

Nếu cần state machine, có thể sử dụng:

```text
PENDING
PROCESSING
READY
STALE
FAILED
```

Nhưng phải dựa trên architecture hiện tại.

Ý nghĩa:

### PENDING

Job đã được tạo nhưng chưa xử lý.

### PROCESSING

TTS đang generate.

### READY

Audio được generate từ current chapter content version và có thể được sử dụng.

### STALE

Audio được generate từ một content version cũ và không còn là audio hiện tại.

### FAILED

TTS generation thất bại.

Phải định nghĩa rõ các state transition.

---

# 10. TTS SNAPSHOT VERSION

Khi User yêu cầu TTS:

Không chỉ xác định:

```text
chapterId
```

Mà phải snapshot:

```text
chapterId
+
contentVersion
```

Ví dụ:

User yêu cầu TTS.

Backend đọc:

```text
chapterId = 100
contentVersion = 7
```

TTS job phải lưu:

```text
chapterId = 100
contentVersion = 7
```

Đây là version mà TTS job đang generate.

Không được lấy content mới một cách không kiểm soát giữa quá trình xử lý.

---

# 11. TTS MUST USE A CONSISTENT CONTENT SNAPSHOT

TTS phải generate từ đúng content snapshot tương ứng với version đã ghi nhận.

Ví dụ:

```text
Chapter v7
    ↓
TTS Job snapshot v7
    ↓
Generate audio from v7 content
```

Không được:

```text
snapshot version = 7
nhưng lúc generate lại load content version = 8
```

Nếu architecture hiện tại có thể xảy ra điều này, phải sửa.

---

# 12. CRITICAL RACE CONDITION

Bắt buộc xử lý case:

```text
T0:
Chapter version = 7

T1:
User request TTS

TTS snapshot:
version = 7

T2:
TTS bắt đầu generate

T3:
Admin sửa chapter

Chapter version:
7 → 8

T4:
TTS version 7 hoàn thành
```

KHÔNG được attach audio version 7 thành current audio.

Backend phải re-check:

```text
generationVersion == currentChapterVersion
```

Nếu:

```text
7 != 8
```

thì generated audio phải được coi là:

```text
STALE
```

hoặc cleanup theo policy.

Không được:

* update current audio reference
* mark audio version 7 là current READY
* overwrite audio version 8
* thay thế current audio
* phục vụ nó như audio của chapter version 8

---

# 13. DOUBLE VERSION VALIDATION

Version phải được kiểm tra ít nhất hai lần.

## BEFORE TTS

Snapshot:

```text
chapterVersion = 7
```

## AFTER TTS

Re-read:

```text
currentChapterVersion
```

So sánh:

```text
generationVersion
vs
currentChapterVersion
```

Nếu khác:

```text
STALE
```

Không được chỉ check version ở đầu request.

---

# 14. TRANSACTION POOL REQUIREMENT

Đây là requirement bắt buộc.

External TTS API KHÔNG được gọi bên trong một database transaction dài.

TUYỆT ĐỐI tránh:

```text
@Transactional
    ↓
load chapter
    ↓
call TTS API
    ↓
wait 10–60 seconds
    ↓
save audio
    ↓
commit
```

Flow đúng phải tách thành các phase:

## Phase 1 — Short DB Transaction

* authenticate user
* validate chapter access
* load chapter
* snapshot contentVersion
* snapshot content
* create/update TTS job

Commit transaction.

DB connection phải được release.

## Phase 2 — External TTS

Gọi:

* Gemini
* ElevenLabs
* Google TTS
* hoặc provider hiện tại

Không giữ DB transaction mở.

## Phase 3 — Short DB Transaction

Sau khi TTS hoàn thành:

* load current chapter version
* compare version
* save audio metadata
* mark READY hoặc STALE

Commit.

Mục tiêu:

```text
NO DB CONNECTION SHOULD REMAIN OCCUPIED
WHILE WAITING FOR EXTERNAL TTS API
```

Điều này đặc biệt quan trọng vì project đã từng có vấn đề connection/transaction pool.

---

# 15. CURRENT AUDIO RESOLUTION

Khi User yêu cầu audio hiện tại của chapter:

Backend phải resolve:

```text
currentChapterVersion
```

Sau đó tìm:

```text
chapterId = X
AND
contentVersion = currentChapterVersion
AND
status = READY
```

Nếu tồn tại:

```text
RETURN AUDIO
```

Nếu không tồn tại:

```text
AUDIO_NOT_READY
```

hoặc status tương đương.

KHÔNG được fallback audio cũ.

---

# 16. NEVER FALLBACK TO OLD AUDIO

Ví dụ:

```text
Chapter:
version = 8

Audio:
version 7 = READY
version 8 = NOT FOUND
```

Không được trả:

```text
audio version 7
```

để "tạm nghe".

Điều này sẽ tạo:

```text
Chapter v8
+
Audio v7
```

Đây là trạng thái không hợp lệ.

Nếu business muốn cho phép User nghe version cũ thì phải tạo business rule riêng.

Không tự ý implement.

---

# 17. AUDIO STORAGE

Không nên dùng một artifact mutable duy nhất:

```text
/audio/chapter-10.mp3
```

theo kiểu overwrite.

Ưu tiên versioned/immutable storage key:

```text
/audio/chapter-10/v7.mp3
/audio/chapter-10/v8.mp3
/audio/chapter-10/v9.mp3
```

hoặc:

```text
/audio/chapter-10/{audioUuid}.mp3
```

Database phải là source of truth để xác định audio thuộc version nào.

Filename hoặc URL không được là source of truth.

---

# 18. AUDIO FILE LIFECYCLE

Khi chapter được sửa:

```text
Chapter v7
Audio v7 READY
       ↓
Admin edits chapter
       ↓
Chapter v8
       ↓
Audio v7 STALE
```

Không nhất thiết phải xóa audio ngay.

Có thể:

```text
STALE
 ↓
Retention period
 ↓
Cleanup
```

Không xóa ngay nếu có khả năng:

* User đang nghe
* Request đang stream
* cần rollback
* cần audit
* TTS job vẫn đang tham chiếu

Phải đánh giá storage policy hiện tại.

---

# 19. ADMIN UPDATE EVENT

Khi chapter content thay đổi:

```text
Chapter v7
    ↓
Update
    ↓
Chapter v8
```

Có thể phát event:

```json
{
  "type": "CHAPTER_UPDATED",
  "chapterId": 100,
  "contentVersion": 8
}
```

Event phải được phát sau khi database update thành công.

Không gửi event báo update nếu transaction update thất bại.

---

# 20. REALTIME USER NOTIFICATION

User đang đọc chapter không nên bắt buộc reload để biết chapter đã thay đổi.

Audit xem project hiện tại có:

* WebSocket
* SSE
* STOMP
* realtime event system

hay chưa.

Nếu project chưa có realtime:

Ưu tiên đánh giá SSE trước WebSocket nếu requirement chủ yếu là:

```text
SERVER → BROWSER
```

Ví dụ:

```text
Admin updates chapter
        ↓
Backend
        ↓
SSE
        ↓
User browser
```

Frontend nhận:

```json
{
  "type": "CHAPTER_UPDATED",
  "chapterId": 100,
  "contentVersion": 8
}
```

---

# 21. FRONTEND CHAPTER VERSION

Frontend phải track:

```text
currentChapterId
currentContentVersion
```

Ví dụ:

```text
currentChapterId = 100
currentContentVersion = 7
```

Nếu nhận event:

```text
chapterId = 100
contentVersion = 8
```

thì:

```text
8 > 7
```

=> chapter hiện tại đã stale.

Hiển thị notification:

```text
Chapter này vừa được cập nhật.
```

Có thể có button:

```text
[Đọc nội dung mới]
```

Không tự động reload giữa lúc User đang đọc.

---

# 22. USER CURRENTLY PLAYING OLD AUDIO

Nếu User đang nghe audio version 7:

```text
User listening
    ↓
Admin updates chapter
    ↓
Chapter version 8
```

Mặc định:

KHÔNG tự động stop audio đang phát.

Không:

* force stop
* restart
* đổi audio giữa chừng
* reset playback position

trừ khi business requirement yêu cầu.

Thay vào đó:

```text
Audio v7 vẫn có thể tiếp tục phát.
UI thông báo chapter đã được cập nhật.
```

Khi User chọn:

```text
[Đọc nội dung mới]
```

thì:

1. Stop old audio.
2. Load new chapter content.
3. Resolve audio của current version.
4. Nếu audio READY → phát audio mới.
5. Nếu chưa READY → trigger TTS hoặc hiển thị trạng thái phù hợp.

---

# 23. FRONTEND RACE CONDITION

Phải xử lý case:

```text
Request A:
Chapter 10 version 7

Request B:
Chapter 10 version 8
```

Nếu:

```text
Response B về trước
Response A về sau
```

thì response A KHÔNG được overwrite state version 8.

Có thể sử dụng:

* requestId
* contentVersion
* AbortController
* state validation

tùy architecture hiện tại.

Nguyên tắc:

```text
OLD ASYNC RESPONSE
MUST NOT OVERWRITE NEWER STATE
```

---

# 24. MULTIPLE TTS REQUESTS

Xử lý case:

```text
User click:
Generate TTS
Generate TTS
Generate TTS
```

Không tạo duplicate TTS job giống nhau nếu không cần.

Có thể dùng logical identity:

```text
chapterId
+
contentVersion
+
voice
+
language
+
model
```

Nếu hệ thống chỉ có một voice/model:

```text
chapterId + contentVersion
```

có thể là generation identity.

Nếu hệ thống hỗ trợ nhiều:

* voice
* language
* model
* speed

thì phải đưa các tham số thực sự ảnh hưởng đến audio vào generation key.

Không khóa toàn bộ chapter nếu nhiều TTS configuration hợp lệ.

---

# 25. IDEMPOTENCY

TTS generation cần có cơ chế idempotency phù hợp.

Nếu cùng một:

```text
chapterId
contentVersion
voice
language
model
```

đã có:

```text
PROCESSING
```

thì không tạo duplicate job nếu không cần.

Nếu đã:

```text
READY
```

thì không generate lại một cách vô ích.

Phải audit logic hiện tại trước khi implement.

---

# 26. ADMIN EDIT MULTIPLE TIMES

Phải xử lý case:

```text
Chapter v7
 ↓
v8
 ↓
v9
 ↓
v10
```

Trong khi TTS jobs:

```text
TTS v7
TTS v8
TTS v9
```

đang chạy.

Khi hoàn thành:

```text
TTS v7 → STALE
TTS v8 → STALE
TTS v9 → STALE
```

Chỉ audio của:

```text
v10
```

mới có thể trở thành current audio.

Không được để "job hoàn thành cuối cùng" quyết định current audio.

Current chapter version mới là source of truth.

---

# 27. CONTENT HASH

Đánh giá có cần:

```text
contentHash
```

hay không.

Có thể sử dụng:

```text
SHA-256(content)
```

để kiểm tra integrity.

Nhưng:

```text
contentVersion
```

là business version.

```text
contentHash
```

là integrity verification.

Không dùng hash thay thế version nếu versioning đã đủ.

Chỉ thêm hash nếu có lợi ích rõ ràng.

---

# 28. CACHE

Audit:

* Redis
* Spring Cache
* CDN
* Browser cache
* HTTP cache
* localStorage
* sessionStorage

Nếu có cache:

Không để cache trả audio version cũ như current audio.

Ưu tiên immutable/versioned resource:

```text
chapter/10/audio/v8
```

Cache invalidation là lớp bổ sung.

Không dùng cache busting để thay thế domain versioning.

---

# 29. HTTP CACHE

Nếu phù hợp, có thể sử dụng:

* ETag
* Last-Modified
* Cache-Control

Nhưng phải hiểu:

HTTP caching không thay thế:

```text
contentVersion
```

Database/domain version vẫn là source of truth.

---

# 30. AUTHORIZATION / XU / VIP

Versioning không được tạo ra một lỗ hổng bypass business authorization.

Mọi audio version đều phải tuân thủ:

* authentication
* chapter access
* Xu unlock
* VIP
* purchase entitlement

Ví dụ:

User chưa unlock Chapter 100.

User không được:

```text
GET old audio v7
```

để bypass việc mua chapter.

Authorization phải được kiểm tra trước khi trả audio.

---

# 31. IMPORTANT: CLIENT MUST NOT PROVIDE TRUSTED CONTENT

Frontend không được gửi:

```json
{
  "chapterId": 100,
  "chapterContent": "..."
}
```

để backend dùng làm source content cho TTS.

Backend phải tự lấy content từ database/storage.

Client chỉ nên cung cấp:

```json
{
  "chapterId": 100
}
```

Backend:

1. authenticate
2. authorize
3. load chapter
4. snapshot version
5. snapshot content
6. generate TTS

---

# 32. TTS FAILURE SCENARIOS

Phải xử lý:

### Case A

TTS thất bại.

### Case B

TTS thành công nhưng DB update thất bại.

### Case C

TTS thành công nhưng chapter đã thay đổi version.

### Case D

Storage upload thành công nhưng DB save thất bại.

### Case E

DB metadata save thành công nhưng audio file không tồn tại.

### Case F

User request TTS nhiều lần.

### Case G

Admin sửa chapter trong lúc TTS đang chạy.

### Case H

Admin sửa chapter nhiều lần liên tiếp.

### Case I

User reload sau khi chapter update.

### Case J

User không reload.

### Case K

User đang nghe audio cũ khi Admin update.

### Case L

Hai tab cùng yêu cầu TTS.

### Case M

Frontend response cũ về sau response mới.

### Case N

User chưa unlock chapter nhưng cố truy cập audio cũ.

---

# 33. DATABASE CONSISTENCY

Database phải có khả năng biểu diễn:

```text
Chapter:
id = 10
contentVersion = 8
```

và:

```text
Audio A:
chapterId = 10
contentVersion = 7
status = STALE

Audio B:
chapterId = 10
contentVersion = 8
status = READY
```

Current audio phải là:

```text
Audio B
```

Không được có:

```text
Chapter v8
Current Audio v7
```

Nếu architecture hiện tại có:

```text
chapter.audioUrl
```

hãy đánh giá có nên tiếp tục dùng field này hay chuyển sang resolve audio bằng:

```text
chapterId + contentVersion
```

Không tự ý xóa field nếu cần migration.

---

# 34. API CHAPTER RESPONSE

Nếu frontend cần version synchronization, API chapter nên expose:

```json
{
  "id": 100,
  "title": "...",
  "content": "...",
  "contentVersion": 8
}
```

Không expose internal fields không cần thiết.

---

# 35. AUDIO API RESPONSE

Audio API nên trả metadata version.

Ví dụ:

```json
{
  "chapterId": 100,
  "contentVersion": 8,
  "status": "READY",
  "url": "..."
}
```

Không chỉ trả:

```json
{
  "url": "..."
}
```

nếu frontend cần version synchronization.

---

# 36. CURRENT AUDIO API RULE

Khi gọi:

```text
GET /chapters/{chapterId}/audio
```

Backend phải:

1. Authenticate.
2. Authorize.
3. Load current chapter.
4. Read current contentVersion.
5. Find READY audio matching current contentVersion.
6. Return matching audio.

Nếu không tìm thấy:

```text
Do NOT return an older audio version.
```

---

# 37. OLD AUDIO CLEANUP

Audio STALE không nhất thiết phải xóa ngay.

Đề xuất lifecycle:

```text
READY
 ↓
Chapter content changes
 ↓
STALE
 ↓
Retention period
 ↓
Cleanup
```

Retention period phải configurable nếu cần.

Không hard-code một khoảng thời gian vô lý.

Nếu storage hiện tại là local filesystem:

phải đảm bảo cleanup không xóa file đang được sử dụng.

Nếu object storage:

có thể dùng lifecycle policy nếu phù hợp.

---

# 38. EXISTING PRODUCTION DATA

Đây là project cá nhân nhưng phải coi dữ liệu hiện tại là production data.

Nếu database đang có audio cũ nhưng chưa có:

```text
contentVersion
```

KHÔNG được tự động gán version một cách mù quáng.

Phải audit:

* audio được generate khi nào
* chapter content hiện tại có giống content lúc generate không
* metadata hiện có
* createdAt
* updatedAt

Sau đó đề xuất migration strategy.

Có thể:

* mark legacy
* regenerate
* map về current version nếu có bằng chứng chắc chắn
* invalidate

Phải chọn strategy dựa trên dữ liệu thực tế.

Không được:

```text
UPDATE all audio SET contentVersion = currentVersion
```

mà không chứng minh audio thực sự tương ứng với current content.

---

# 39. DATABASE MIGRATION

Sử dụng migration mechanism hiện tại của project.

Nếu project dùng:

* Flyway
* Liquibase
* hoặc migration mechanism khác

hãy reuse.

Không:

```text
DROP TABLE
```

hoặc:

```text
DROP DATABASE
```

để giải quyết migration.

Phải đảm bảo:

* existing data safety
* backward compatibility nếu cần
* rollback strategy nếu phù hợp

---

# 40. DO NOT OVERENGINEER

Đây là production cá nhân, không phải enterprise-scale system.

Không tự ý thêm:

* Kafka
* RabbitMQ
* Kubernetes
* microservices
* event sourcing
* distributed locks
* Redis
* complex event bus

nếu project hiện tại chưa cần.

Ưu tiên:

```text
Database versioning
+
Service layer
+
Existing async mechanism
+
Versioned audio storage
+
SSE nếu cần
```

Nếu project đã có infrastructure tương ứng thì reuse.

Không tạo thêm infrastructure chỉ vì "production".

---

# 41. SSE VS WEBSOCKET

Nếu project chưa có realtime system:

Đánh giá SSE trước.

Use case hiện tại chủ yếu là:

```text
Server
  ↓
Browser
```

Event:

```text
CHAPTER_UPDATED
```

SSE có thể phù hợp hơn WebSocket vì không cần full bidirectional communication.

Nếu project đã có WebSocket:

Reuse WebSocket.

Không tạo cả SSE và WebSocket cho cùng một use case.

---

# 42. IMPLEMENTATION ORDER

Sau khi audit và được phép implement, thực hiện theo thứ tự:

## Step 1

Define content version.

## Step 2

Database migration.

## Step 3

Update Admin chapter update flow.

## Step 4

Increment contentVersion when content changes.

## Step 5

Add audio contentVersion.

## Step 6

Update TTS generation flow.

## Step 7

Snapshot content + version before external TTS call.

## Step 8

Ensure external TTS call is outside DB transaction.

## Step 9

Re-check chapter version after TTS completes.

## Step 10

Mark audio READY or STALE.

## Step 11

Fix current audio resolution.

## Step 12

Prevent fallback to old audio.

## Step 13

Add frontend version tracking.

## Step 14

Add realtime notification if appropriate.

## Step 15

Handle current audio playback behavior.

## Step 16

Add duplicate TTS protection.

## Step 17

Add cleanup strategy.

## Step 18

Add tests.

## Step 19

Run regression tests.

---

# 43. TESTING REQUIREMENTS

Bắt buộc có tests cho:

## Unit Tests

* contentVersion increment
* audio version matching
* stale detection
* TTS result validation
* authorization
* duplicate request handling

## Integration Tests

* Admin update chapter
* TTS generation
* DB transaction boundary
* audio resolution
* stale audio rejection
* Xu/VIP access

## Concurrency Tests

Đặc biệt:

```text
TTS starts
+
Admin updates chapter
+
TTS finishes
```

Expected:

```text
TTS result = STALE
```

Không được trở thành current audio.

---

# 44. CRITICAL ACCEPTANCE TEST

Bắt buộc test scenario sau:

```text
1. Chapter version = 1.

2. Không có audio.

3. User request TTS.

4. TTS job snapshot:
   chapterId = 10
   contentVersion = 1

5. TTS bắt đầu.

6. Admin sửa chapter.

7. Chapter version = 2.

8. TTS version 1 hoàn thành.

9. Backend re-check current chapter version.

10. Detect:
    generationVersion = 1
    currentVersion = 2

11. Audio version 1 MUST NOT become current audio.

12. Audio version 1 = STALE hoặc cleanup theo policy.

13. User request current audio.

14. Backend MUST NOT return audio version 1.

15. User receives chapter update notification.

16. User loads chapter version 2.

17. TTS version 2 is generated.

18. Audio version 2 becomes READY.

19. User receives audio version 2.

20. Chapter v2 + Audio v2 is the only valid current combination.
```

---

# 45. MULTI-TAB TEST

Test:

```text
Tab A → Chapter 10 v1
Tab B → Chapter 10 v1

Admin → Chapter 10 v2

Tab A receives update
Tab B receives update
```

Không được để một tab giữ state cũ mà không có cách phát hiện stale.

---

# 46. RELOAD TEST

Test:

```text
Chapter v1
Audio v1 READY

Admin edits
Chapter v2

User reloads
```

Expected:

```text
Frontend receives Chapter v2
Audio API resolves v2
Audio v1 MUST NOT be returned
```

---

# 47. CURRENT PLAYBACK TEST

Test:

```text
User playing Audio v1
Admin edits chapter
Chapter becomes v2
```

Expected default behavior:

```text
Audio v1 is not abruptly interrupted.

User receives:
"Chapter này vừa được cập nhật."

User can choose:
"Đọc nội dung mới"
```

---

# 48. TRANSACTION POOL TEST

Test và kiểm tra rằng:

```text
TTS request
    ↓
DB transaction
    ↓
snapshot data
    ↓
COMMIT
    ↓
DB connection released
    ↓
TTS API call
    ↓
TTS finishes
    ↓
short DB transaction
    ↓
version validation
```

Không được:

```text
DB transaction
    ↓
WAIT FOR TTS
```

Nếu có monitoring/metrics hiện tại:

kiểm tra connection pool behavior trước và sau implementation.

---

# 49. SECURITY TEST

Test:

```text
User chưa unlock chapter
        ↓
GET old audio version
```

Expected:

```text
DENIED
```

Không được dùng version cũ để bypass:

* Xu
* VIP
* chapter unlock
* authentication

---

# 50. FINAL ARCHITECTURE TARGET

Architecture mục tiêu:

```text
                         ADMIN
                           │
                           ▼
                    Edit Chapter
                           │
                           ▼
                 ┌──────────────────┐
                 │ Chapter Service  │
                 │                  │
                 │ contentVersion++ │
                 └────────┬─────────┘
                          │
              ┌───────────┴───────────┐
              │                       │
              ▼                       ▼
          DATABASE              REALTIME EVENT
                                      │
                                      ▼
                                  SSE/WebSocket
                                      │
                                      ▼
                                  USER BROWSER


USER
 │
 │ Request TTS
 ▼
Backend
 │
 ├── Authenticate
 ├── Authorize
 ├── Load chapter
 ├── Snapshot content
 ├── Snapshot contentVersion
 └── Create TTS job
 │
 ▼
COMMIT
 │
 ▼
DB connection released
 │
 ▼
External TTS API
 │
 ▼
Audio generated
 │
 ▼
Short DB transaction
 │
 ├── Load current chapter version
 ├── Compare versions
 │
 ├── SAME
 │     ↓
 │    READY
 │
 └── DIFFERENT
       ↓
      STALE
```

---

# 51. FINAL BUSINESS INVARIANTS

Implementation phải đảm bảo các invariant sau:

## Invariant 1

```text
CURRENT_AUDIO.contentVersion
==
CURRENT_CHAPTER.contentVersion
```

## Invariant 2

```text
TTS_RESULT.contentVersion
!=
CURRENT_CHAPTER.contentVersion
```

thì:

```text
TTS_RESULT MUST NOT BECOME CURRENT AUDIO
```

## Invariant 3

External TTS API không được giữ DB transaction mở.

## Invariant 4

Audio cũ không được fallback thành audio hiện tại.

## Invariant 5

Frontend không phải source of truth cho chapter/audio version.

## Invariant 6

Database/domain versioning là source of truth.

## Invariant 7

Chapter access control luôn được áp dụng trước khi trả audio.

---

# 52. FINAL IMPLEMENTATION REPORT

Sau khi implementation hoàn thành, báo cáo:

## 1. Changed Files

Liệt kê toàn bộ file đã thay đổi.

## 2. Database Changes

Mô tả migration.

## 3. Chapter Versioning

Mô tả contentVersion.

## 4. Audio Versioning

Mô tả audio/contentVersion relationship.

## 5. TTS Lifecycle

Mô tả flow TTS mới.

## 6. Transaction Boundary

Chứng minh external TTS API không còn nằm trong transaction dài.

## 7. Race Condition Handling

Mô tả cách xử lý Admin edit trong lúc TTS đang chạy.

## 8. Audio Resolution

Mô tả cách backend chọn current audio.

## 9. Stale Audio

Mô tả lifecycle STALE và cleanup.

## 10. Frontend Synchronization

Mô tả cách frontend phát hiện chapter update.

## 11. Realtime

Mô tả SSE/WebSocket nếu được triển khai.

## 12. Security

Mô tả authorization với Xu/VIP/chapter unlock.

## 13. Tests

Liệt kê tests đã chạy.

## 14. Migration Risk

Mô tả rủi ro với existing data.

## 15. Remaining Risks

Nêu rõ các vấn đề còn lại nếu có.

---

# 53. IMPORTANT AGENT RULES

1. Không code ngay trước khi audit.
2. Không sửa file ngoài scope nếu không có lý do.
3. Không phá business logic hiện tại.
4. Không phá Xu/VIP/chapter purchase.
5. Không làm lộ chapter premium thông qua audio API.
6. Không giữ DB transaction trong lúc chờ TTS.
7. Không dùng reload/cache busting làm giải pháp chính.
8. Không fallback audio cũ.
9. Không overwrite audio artifact cũ nếu làm mất khả năng xác định version.
10. Không tự ý thêm infrastructure phức tạp.
11. Không xóa production data.
12. Không migrate existing audio một cách mù quáng.
13. Không thay đổi behavior của audio player ngoài yêu cầu.
14. Không force-stop audio đang phát mặc định.
15. Không để async response cũ overwrite state mới.
16. Không để TTS job cũ trở thành current audio.
17. Không tin content do frontend gửi lên.
18. Không coi filename hoặc URL là source of truth.
19. Database/domain version là source of truth.
20. Nếu phát hiện architecture hiện tại khác với assumption trong task này, hãy báo cáo trước khi thay đổi.

---

# FINAL GOAL

Sau implementation, hệ thống phải đảm bảo:

```text
Chapter v1
    ↓
TTS v1
    ↓
Audio v1 READY
```

Admin sửa chapter:

```text
Chapter v1
    ↓
Chapter v2
```

Ngay lập tức:

```text
Audio v1 = STALE
```

Nếu TTS v1 đang chạy:

```text
TTS v1 completes
        ↓
Version check
        ↓
v1 != v2
        ↓
STALE
```

Không được trở thành current audio.

User đang đọc:

```text
Chapter v1
```

nhận notification:

```text
Chapter này vừa được cập nhật.
```

User chọn load version mới:

```text
Chapter v2
        ↓
Audio v2 nếu READY
        ↓
hoặc generate TTS v2
```

Cuối cùng hệ thống chỉ coi:

```text
Chapter v2
+
Audio v2
```

là cặp hợp lệ.

Tuyệt đối không để:

```text
Chapter v2
+
Audio v1
```

được coi là current/valid combination.

Đây là mục tiêu cốt lõi của TASK 05.

```
```
