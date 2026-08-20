# TASK — IMPLEMENT AI STORY ASSISTANT FOR CURRENT CHAPTER

Bạn hãy đóng vai Senior Full-Stack Engineer + AI Integration Engineer, có kinh nghiệm với:

- Spring Boot
- Java
- REST API
- Frontend hiện tại của project
- Gemini API
- LLM integration
- Prompt engineering
- Secure API key management
- Context management
- UX/UI cho AI assistant

==================================================
1. MỤC TIÊU
==================================================

Tôi muốn implement thêm một tính năng AI Assistant vào website đọc truyện/audio hiện tại.

Tính năng này là một trợ lý ảo AI nhỏ xuất hiện trong giao diện đọc truyện.

UI mong muốn:

- Một mini chat/message box nhỏ.
- Vị trí: góc dưới bên phải màn hình.
- Nằm ngay phía trên bottom bar hiện tại của trang đọc truyện.
- Không che khuất nội dung truyện.
- Không phá vỡ layout hiện tại.
- Có thể mở rộng/thu nhỏ.
- Khi đóng thì chỉ còn một nút/icon AI nhỏ để mở lại.
- Khi mở thì hiển thị giao diện chat nhỏ.
- Responsive trên desktop và mobile.

Mục đích chính:

> AI hỗ trợ người dùng hiểu và tóm tắt nội dung của CHAPTER MÀ USER ĐANG ĐỌC.

AI phải sử dụng context của chapter hiện tại.

==================================================
2. QUAN TRỌNG — KHÔNG CODE NGAY
==================================================

Trước tiên hãy audit codebase hiện tại.

Không được tự ý tạo architecture mới nếu project đã có:

- Reader page
- Chapter page
- Chapter API
- Story API
- Authentication
- User context
- Backend service
- API client
- UI component
- Bottom bar
- Loading component
- Error handling

Hãy tìm và sử dụng lại những component/service hiện có nếu phù hợp.

Trước khi code, hãy xác định:

1. Frontend framework đang sử dụng.
2. Backend framework.
3. Cấu trúc project.
4. Reader page/component hiện tại.
5. Chapter data được load ở đâu.
6. Chapter content hiện đang nằm ở đâu.
7. API lấy chapter hiện tại.
8. Authentication hiện tại.
9. Bottom bar nằm ở component nào.
10. API convention hiện tại.
11. Configuration/env convention hiện tại.
12. Cách frontend gọi backend hiện tại.

Sau đó mới đề xuất implementation.

==================================================
3. AI MODEL
==================================================

Tôi muốn sử dụng Gemini model:

Gemini 3.1 Flash Lite

Không hard-code API key.

Không hard-code URL/API credential trong source code.

Tạo configuration trong `.env` để tôi tự điền sau.

Ví dụ architecture:

GEMINI_API_URL=
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.1-flash-lite

Tên biến có thể điều chỉnh để phù hợp với convention hiện tại của project.

Tôi sẽ tự điền giá trị thật sau.

Không được tự điền API key giả rồi commit vào project.

==================================================
4. GEMINI API ARCHITECTURE
==================================================

Không để frontend gọi trực tiếp Gemini API nếu điều đó làm lộ API key.

Architecture ưu tiên:

User
 ↓
Reader UI
 ↓
Backend API
 ↓
Gemini Service
 ↓
Gemini API

Ví dụ:

POST /api/ai/story-assistant

Request:

{
  "chapterId": "...",
  "message": "Tóm tắt chapter này cho tôi"
}

Backend:

1. Authenticate user.
2. Validate chapterId.
3. Load chapter content từ database/service hiện tại.
4. Build AI prompt.
5. Gọi Gemini API.
6. Parse response.
7. Return response cho frontend.

Frontend không được biết:

- GEMINI_API_KEY
- Gemini secret
- backend credential.

==================================================
5. CHAPTER CONTEXT
==================================================

AI phải hiểu rằng nó đang hỗ trợ người dùng đối với CHAPTER HIỆN TẠI.

Ví dụ:

User đang đọc:

Story A
Chapter 25

AI phải nhận context:

- story title
- chapter number
- chapter title
- chapter content

Nếu cần, có thể bổ sung metadata phù hợp.

Không gửi toàn bộ database hoặc toàn bộ truyện nếu không cần thiết.

Không load toàn bộ story chỉ để trả lời câu hỏi liên quan đến chapter hiện tại.

==================================================
6. CONTEXT STRATEGY
==================================================

Hãy phân tích độ dài chapter hiện tại trước khi gửi Gemini.

Nếu chapter ngắn:

Có thể gửi toàn bộ chapter content.

Nếu chapter rất dài:

Không được mù quáng gửi toàn bộ content.

Hãy đề xuất strategy phù hợp:

- truncate
- chunking
- summarization
- pre-generated chapter summary
- context window management

Ưu tiên giải pháp đơn giản trước.

Không implement RAG/vector database nếu use case hiện tại chưa cần.

Mục tiêu hiện tại chỉ là:

> AI assistant có thể tóm tắt và trả lời câu hỏi dựa trên chapter đang đọc.

==================================================
7. AI SYSTEM PROMPT
==================================================

Tạo system instruction rõ ràng cho AI.

AI phải hiểu:

- Nó là trợ lý đọc truyện.
- Nó đang hỗ trợ user với chapter hiện tại.
- Ưu tiên sử dụng nội dung chapter được cung cấp.
- Không được tự bịa nội dung không có trong chapter.
- Nếu thông tin không có trong chapter, phải nói rõ.
- Không giả vờ biết những gì không được cung cấp.
- Trả lời bằng ngôn ngữ phù hợp với người dùng.
- Nếu user hỏi tóm tắt chapter → tóm tắt chapter.
- Nếu user hỏi nhân vật → chỉ dựa trên context có sẵn.
- Nếu user hỏi diễn biến → giải thích dựa trên chapter.
- Không tự ý tiết lộ nội dung các chapter sau nếu context không được cung cấp.

Ví dụ instruction:

"You are an AI reading assistant for an online story platform. Your primary task is to help the user understand the current chapter. Base your answers primarily on the provided chapter content. Do not invent facts. If the requested information is not available in the current chapter context, clearly state that you don't have enough information."

Nhưng hãy điều chỉnh prompt phù hợp với implementation thực tế.

==================================================
8. CÁC CHỨC NĂNG AI BAN ĐẦU
==================================================

Phiên bản đầu tiên cần hỗ trợ tối thiểu:

### Feature 1 — Tóm tắt chapter

User có thể hỏi:

"Tóm tắt chapter này"

AI trả về summary ngắn gọn.

### Feature 2 — Hỏi về chapter

Ví dụ:

"Nhân vật chính đã làm gì?"

"Chuyện gì xảy ra ở cuối chapter?"

"Nhân vật A có gặp nhân vật B không?"

AI trả lời dựa trên chapter hiện tại.

### Feature 3 — Giải thích

Ví dụ:

"Giải thích đoạn này"

"Quan hệ giữa hai nhân vật này là gì?"

==================================================
9. UX/UI MINI CHAT BOX
==================================================

Thiết kế một AI Assistant box nhỏ.

Vị trí:

BOTTOM RIGHT

Ngay phía trên bottom bar hiện tại.

Không che:

- bottom bar
- audio controls
- chapter content
- navigation controls

Nếu bottom bar có chiều cao động, hãy tính vị trí tương đối thay vì hard-code một giá trị có thể gây overlap.

Ví dụ concept:

             ┌──────────────────────┐
             │ AI Story Assistant   │
             │                      │
             │ Tóm tắt chapter này  │
             │                      │
             │ Bạn: ...             │
             │ AI: ...              │
             │                      │
             │ [Nhập câu hỏi...]    │
             └──────────────────────┘
                        │
                        ▼
             ┌──────────────────────┐
             │      Bottom Bar      │
             └──────────────────────┘

Khi collapsed:

                    ┌─────┐
                    │ AI  │
                    └─────┘
             ┌──────────────────────┐
             │      Bottom Bar      │
             └──────────────────────┘

==================================================
10. UI REQUIREMENTS
==================================================

AI box phải:

- nhỏ gọn.
- hiện đại.
- không gây khó chịu.
- không chiếm quá nhiều diện tích.
- có loading state.
- có error state.
- có empty state.
- có input.
- có send button.
- có close/collapse.
- hỗ trợ Enter để gửi.
- không gửi request khi input rỗng.
- disable send khi đang request nếu phù hợp.
- tự scroll xuống message mới nhất.
- giữ lịch sử chat trong phiên đọc hiện tại.

Không cần persistence chat vào database ở phiên bản đầu tiên nếu chưa cần.

==================================================
11. QUICK ACTIONS
==================================================

Để UX tốt hơn, khi mở AI box có thể hiển thị các button:

- "Tóm tắt chapter"
- "Các nhân vật chính"
- "Điều gì đã xảy ra?"
- "Giải thích chapter"

Các quick action chỉ là shortcut để tạo prompt gửi backend.

Không hard-code logic AI ở frontend.

==================================================
12. CHAT STATE
==================================================

Frontend cần quản lý:

- messages
- loading
- error
- input
- open/closed
- current chapterId

Ví dụ:

messages:

[
  {
    role: "user",
    content: "Tóm tắt chapter này"
  },
  {
    role: "assistant",
    content: "..."
  }
]

Không cần gửi toàn bộ lịch sử chat lên server nếu không cần.

Hãy phân tích xem backend có cần conversation history hay chỉ cần current question + chapter context.

Ưu tiên kiến trúc đơn giản.

==================================================
13. MULTI-TURN CONVERSATION
==================================================

AI box nên hỗ trợ hội thoại nhiều lượt trong cùng chapter.

Ví dụ:

User:
"Tóm tắt chapter này"

AI:
"..."

User:
"Nhân vật chính có quyết định gì?"

AI:
"..."

User:
"Tại sao anh ta lại làm vậy?"

AI:
"..."

Nếu cần conversation context, hãy thiết kế context window hợp lý.

Không gửi lịch sử chat vô hạn.

Giới hạn số message hoặc token history phù hợp.

==================================================
14. CHAPTER CHANGE
==================================================

Đây là yêu cầu rất quan trọng.

Khi user chuyển:

Chapter 10
→
Chapter 11

AI context phải được reset hoặc cập nhật.

Không được để AI trả lời câu hỏi Chapter 11 bằng context của Chapter 10.

Khi chapterId thay đổi:

- clear hoặc reset chat state.
- cập nhật current chapterId.
- cập nhật AI context.

Nếu UI component được giữ lại giữa các chapter, phải đảm bảo state không bị stale.

==================================================
15. API DESIGN
==================================================

Thiết kế API phù hợp với backend hiện tại.

Ví dụ:

POST /api/ai/story-assistant

Request:

{
  "chapterId": 123,
  "message": "Tóm tắt chapter này"
}

Response:

{
  "message": "..."
}

Có thể bổ sung:

{
  "conversationId": "...",
  "message": "...",
  "usage": ...
}

nhưng chỉ thêm nếu thực sự cần.

Không tạo API phức tạp không cần thiết.

==================================================
16. SECURITY
==================================================

API phải yêu cầu authentication nếu architecture hiện tại yêu cầu user đăng nhập.

Không trust chapter content do frontend gửi lên.

Frontend chỉ gửi:

chapterId

Backend phải tự lấy chapter content từ database/service.

Không cho client gửi:

{
  "chapterContent": "..."
}

rồi backend tin tưởng trực tiếp.

Lý do:

- client có thể sửa content.
- user có thể gửi dữ liệu giả.
- có thể bypass access rules.

Backend phải kiểm tra:

- chapter tồn tại.
- user có quyền truy cập chapter nếu chapter là premium/VIP.
- chapter thuộc story hợp lệ.

AI assistant không được trở thành cách bypass chapter protection.

Ví dụ:

User chưa mua chapter premium.

Không được phép gửi nội dung chapter premium sang Gemini chỉ vì user gọi AI API.

Phải reuse access control hiện tại.

==================================================
17. PREMIUM / VIP ACCESS
==================================================

Nếu project có hệ thống:

- Xu
- chapter purchase
- VIP

thì AI assistant phải tôn trọng access control.

Flow:

User
 ↓
AI request
 ↓
Authenticate
 ↓
Check chapter access
 ↓
Nếu allowed
    ↓
Load chapter content
    ↓
Gemini
 ↓
Response

Nếu không có quyền:

Không gửi chapter content cho Gemini.

Trả lỗi phù hợp.

Không expose nội dung premium.

==================================================
18. GEMINI SERVICE
==================================================

Tạo service abstraction.

Ví dụ:

GeminiService

hoặc tên phù hợp với architecture.

Service chịu trách nhiệm:

- build request
- call Gemini
- timeout
- parse response
- error handling

Controller không được chứa toàn bộ Gemini logic.

Không viết:

Controller
 → HTTP call trực tiếp Gemini

nếu architecture hiện tại hỗ trợ service layer.

==================================================
19. CONFIGURATION
==================================================

Tạo environment variables.

Ví dụ:

GEMINI_API_URL=
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.1-flash-lite

Nếu Gemini API yêu cầu URL khác hoặc model identifier khác theo SDK/API đang sử dụng, hãy kiểm tra tài liệu/API contract hiện tại trước khi implementation.

Không tự đoán endpoint nếu có thể xác minh.

Tạo config class:

GeminiProperties

hoặc tên phù hợp.

Không hard-code:

- API key
- URL
- model
- timeout

nếu những giá trị đó cần configurable.

==================================================
20. TIMEOUT / ERROR HANDLING
==================================================

AI API có thể:

- timeout
- rate limit
- 4xx
- 5xx
- network error
- malformed response

Backend phải xử lý.

Không để exception Gemini làm crash request/thread không kiểm soát.

Frontend phải hiển thị:

"AI hiện không khả dụng. Vui lòng thử lại."

Không expose:

- API key
- stack trace
- internal URL
- Gemini raw error nếu chứa sensitive information

==================================================
21. RATE LIMIT / ABUSE
==================================================

AI API có cost.

Phải đánh giá rate limit.

Không nhất thiết implement một hệ thống quota phức tạp ngay.

Nhưng phải có protection tối thiểu.

Ví dụ:

- giới hạn request/user
- chống spam click
- debounce/throttle
- không gửi request khi message rỗng
- disable button khi request đang pending

Nếu backend hiện đã có rate limiting mechanism, hãy reuse.

==================================================
22. TOKEN / CONTEXT COST
==================================================

Đây là điểm quan trọng.

Không gửi chapter content lặp lại quá nhiều lần nếu có cách tối ưu hợp lý.

Ví dụ:

User hỏi:

1. Tóm tắt.
2. Ai là nhân vật chính?
3. Chuyện gì xảy ra cuối chapter?

Nếu mỗi request đều gửi một chapter cực dài, token cost có thể tăng.

Hãy đánh giá:

- chapter caching
- summary caching
- conversation context
- request size
- token limit

Nhưng không implement caching phức tạp nếu chưa cần.

==================================================
23. SUMMARY CACHE
==================================================

Có thể cân nhắc cache summary.

Ví dụ:

Chapter 100
 ↓
Generate summary
 ↓
Cache
 ↓
Các request sau dùng summary nếu phù hợp

Nhưng:

Không tự động tạo bảng/cache nếu chưa cần.

Hãy đánh giá:

- chapter content có thay đổi không?
- summary có cần regenerate không?
- cache invalidation thế nào?

Nếu chapter ít thay đổi, summary caching có thể hữu ích.

==================================================
24. PROMPT INJECTION / CONTENT SAFETY
==================================================

Chapter content là dữ liệu không đáng tin cậy.

AI prompt phải phân biệt:

SYSTEM INSTRUCTION

và:

CHAPTER CONTENT

Không để chapter content dễ dàng override instruction.

Ví dụ:

Nếu chapter text chứa:

"Ignore all previous instructions..."

AI không được coi đó là system instruction.

Đưa chapter content vào context/data section phù hợp.

==================================================
25. OBSERVABILITY
==================================================

Log:

- request started
- chapterId
- userId nếu logging policy cho phép
- response success/failure
- latency
- error category

Không log:

- Gemini API key
- toàn bộ chapter content
- sensitive user information

Nếu có thể, thêm metric:

- AI request count
- success rate
- failure rate
- latency
- token usage nếu Gemini API cung cấp

==================================================
26. DATABASE
==================================================

Không cần tạo database table cho chat history ở phiên bản đầu tiên nếu chưa có yêu cầu persistence.

Mặc định:

Chat history chỉ tồn tại trong browser/session đọc chapter.

Chỉ đề xuất database persistence nếu audit cho thấy project đã có architecture phù hợp hoặc có lý do rõ ràng.

==================================================
27. TESTING
==================================================

Phải thêm/test các trường hợp:

### Normal

- mở AI box.
- hỏi summary.
- hỏi về character.
- hỏi về event.
- multi-turn conversation.

### Chapter change

- Chapter 1 → Chapter 2.
- context phải reset.

### Authentication

- user authenticated.
- user unauthenticated.

### Access control

- free chapter.
- purchased chapter.
- VIP chapter.
- unauthorized premium chapter.

### Gemini

- success.
- timeout.
- 4xx.
- 5xx.
- empty response.

### UI

- desktop.
- mobile.
- bottom bar.
- expanded.
- collapsed.
- loading.
- error.
- long response.

### Security

- client gửi chapterId giả.
- client gửi chapterContent giả.
- user cố truy cập premium chapter qua AI API.

==================================================
28. ACCEPTANCE CRITERIA
==================================================

Tính năng được xem là hoàn thành khi:

1. AI Assistant xuất hiện ở góc dưới bên phải reader.
2. Nằm phía trên bottom bar.
3. Không phá layout hiện tại.
4. Có thể mở/đóng.
5. Có chat UI.
6. User có thể hỏi AI.
7. AI biết chapter hiện tại.
8. AI có thể tóm tắt chapter.
9. AI có thể trả lời câu hỏi dựa trên chapter.
10. Khi chuyển chapter, context được cập nhật.
11. Gemini API key chỉ tồn tại ở backend.
12. `.env` có configuration để tôi tự điền.
13. Không hard-code API key.
14. Backend lấy chapter content từ database/service.
15. Không trust chapterContent từ frontend.
16. Chapter access control vẫn được áp dụng.
17. Premium chapter không bị AI endpoint bypass.
18. Gemini errors được xử lý.
19. UI có loading/error state.
20. Không tạo database chat history nếu chưa cần.
21. Code phù hợp architecture hiện tại.
22. Không phá các chức năng reader/audio hiện tại.

==================================================
29. OUTPUT TRƯỚC KHI IMPLEMENT
==================================================

Trước khi sửa code, hãy báo cáo:

## 1. Current Architecture

Frontend/backend đang hoạt động thế nào.

## 2. Reader Architecture

Reader page/component nằm ở đâu.

## 3. Chapter Data Flow

Chapter được lấy như thế nào.

## 4. Authentication

Authentication hiện tại.

## 5. Chapter Authorization

Chapter access hiện tại.

## 6. Existing API Pattern

Convention API.

## 7. Existing Configuration

.env/application config hiện tại.

## 8. Proposed AI Architecture

Frontend → Backend → Gemini.

## 9. UI Design

Component nào sẽ được tạo/sửa.

## 10. API Design

Endpoint + request + response.

## 11. Context Strategy

Cách truyền chapter context.

## 12. Security

Cách bảo vệ API key và premium content.

## 13. Cost/Token Considerations

Cách hạn chế token usage.

## 14. Files To Change

Danh sách file dự kiến.

KHÔNG IMPLEMENT trước khi hoàn thành audit trên.

==================================================
30. IMPLEMENTATION
==================================================

Sau khi audit xong, hãy implement.

Ưu tiên:

- reuse existing architecture.
- minimal changes.
- clean code.
- separation of concerns.
- secure configuration.
- testability.

Không refactor các module không liên quan.

==================================================
31. OUTPUT SAU KHI IMPLEMENT
==================================================

Sau khi code xong, báo cáo:

## 1. Files Changed

Danh sách file.

## 2. Frontend Changes

AI box/component.

## 3. Backend Changes

Controller/service/configuration.

## 4. Gemini Integration

Cách gọi API.

## 5. Environment Variables

Các biến tôi cần điền.

Ví dụ:

GEMINI_API_URL=
GEMINI_API_KEY=
GEMINI_MODEL=

Không đưa secret thật vào output.

## 6. Chapter Context

AI nhận context như thế nào.

## 7. Security

Authentication + chapter authorization.

## 8. Error Handling

Gemini failure/timeout/rate limit.

## 9. Tests

Tests đã chạy.

## 10. Manual Verification

Hướng dẫn kiểm tra tính năng.

==================================================
32. QUY TẮC CUỐI CÙNG
==================================================

Không được:

- hard-code Gemini API key.
- expose Gemini API key cho frontend.
- gửi chapterContent do client tự cung cấp vào Gemini.
- bypass chapter authorization.
- expose premium chapter content.
- lưu toàn bộ chapter vào browser localStorage nếu không cần.
- tạo database chat history nếu chưa cần.
- thêm vector database/RAG nếu chưa cần.
- thêm infrastructure phức tạp không cần thiết.
- refactor reader toàn bộ.
- thay đổi audio player nếu không liên quan.
- phá bottom bar hiện tại.
- tự ý thay đổi business logic Xu/VIP.

Nếu phát hiện vấn đề thuộc các task khác:

OUT OF SCOPE

Chỉ báo cáo, không tự ý sửa.

MỤC TIÊU CUỐI CÙNG:

Tạo một AI Story Assistant nhỏ, nhẹ và hữu ích:

                ┌─────────────────────────┐
                │   ✨ AI Story Assistant │
                ├─────────────────────────┤
                │                         │
                │ AI: Tôi có thể giúp bạn │
                │ hiểu chapter này.       │
                │                         │
                │ [Tóm tắt chapter]       │
                │ [Nhân vật chính]        │
                │ [Điều gì xảy ra?]       │
                │                         │
                │ Bạn: ................   │
                │                         │
                │              [Send]     │
                └─────────────────────────┘

                         ↓

                    Backend API

                         ↓

                  Gemini 3.1 Flash Lite

                         ↓

              Context = Current Chapter

                         ↓

                    AI Response

AI phải hoạt động như một trợ lý đọc truyện, không phải một chatbot tổng quát.

Ưu tiên:
- đúng context
- bảo mật
- chi phí hợp lý
- UX tốt
- architecture đơn giản
- dễ mở rộng sau này.