# AI Support Assistant — Business & Engineering Rules

> **Status:** Specification / Source of Truth
> **Purpose:** Define the complete business logic, conversation lifecycle, AI behavior, human handoff, WebSocket integration, security, persistence, and edge-case handling for the AI Support feature.
>
> This document MUST be followed by any agent implementing or modifying the AI Support functionality.

---

# 1. Feature Objective

The existing Support system allows users to communicate with Admin/Support Agents.

This feature extends that system by introducing an **AI-first support layer**.

The AI exists to:

* Answer common support questions.
* Explain how website features work.
* Provide free basic guidance.
* Reduce unnecessary workload for Admin/Support Agents.
* Detect cases requiring human intervention.
* Transfer the conversation to a real Support Agent without losing context.

The AI must **extend the existing Support system**, not replace it.

The existing user ↔ admin conversation, message, authentication, notification, unread state, and WebSocket infrastructure should be reused wherever technically appropriate.

---

# 2. Core Product Flow

The intended high-level flow is:

```text
User opens Support
        │
        ├── Chat with AI
        │       │
        │       ├── AI resolves issue
        │       │
        │       └── User requests human
        │                  │
        │                  ▼
        │            Human Handoff
        │                  │
        │                  ▼
        │              Admin Chat
        │
        └── Chat with Support Agent
                    │
                    ▼
                Admin Chat
```

The user must always have a clear path to a human.

AI must never become a barrier preventing the user from contacting Support.

---

# 3. Support Conversation Modes

A Support conversation conceptually has a current mode:

```text
AI_ACTIVE
HANDOFF_REQUESTED
ADMIN_ACTIVE
RESOLVED
```

The actual implementation MUST reuse existing conversation/status fields where possible.

Do not create redundant state fields when the existing data model can represent the same state reliably.

## 3.1 AI_ACTIVE

The conversation is currently being handled by AI.

Rules:

* AI may respond.
* User may send messages.
* User may request human support at any time.
* Conversation history must be persisted.
* AI must not perform privileged operations.

## 3.2 HANDOFF_REQUESTED

A handoff to human support has been requested or is being processed.

Rules:

* AI must stop producing new automatic responses once handoff is committed.
* The conversation becomes eligible for Admin handling.
* Admin must receive a notification through the existing notification/WebSocket mechanism.
* The complete conversation context must remain available.
* Handoff must be idempotent.

## 3.3 ADMIN_ACTIVE

A human Support Agent/Admin owns the active conversation.

Rules:

* Admin becomes the active responder.
* AI automatic replies MUST stop.
* User continues using the existing chat flow.
* Existing WebSocket messaging must be reused.

## 3.4 RESOLVED

The support issue has been resolved.

Rules:

* No automatic AI response should be generated for the resolved conversation.
* Reopening behavior must follow the existing Support business rules.
* A new support interaction must not silently mutate a historically resolved conversation unless the existing system explicitly supports reopening.

---

# 4. First Support Entry

When the user opens Support for the first time and has no active human-support conversation:

The UI should offer:

```text
🤖 Chat with AI
👨‍💻 Chat with Support Agent
```

A short welcome message may be displayed.

Recommended behavior:

> Xin chào! Tôi có thể hỗ trợ bạn giải đáp các vấn đề thường gặp về tài khoản, truyện, VIP, xu, mở khóa chương và cách sử dụng website.
>
> Bạn muốn chat với AI hay gặp tư vấn viên?

The exact wording is a UI decision, but the functionality must remain clear.

The system must NOT automatically create a human-support ticket merely because the user opened the Support screen.

---

# 5. AI Conversation Creation

When the user selects `Chat with AI`:

1. Reuse an existing compatible Support conversation where possible.
2. Otherwise create the minimum required AI-support conversation record.
3. Persist conversation state.
4. Persist user messages.
5. Persist AI responses.
6. Maintain enough history for contextual AI responses.
7. Prevent duplicate active AI conversations for the same user unless the current system explicitly supports multiple simultaneous conversations.

The system must be able to determine:

* Which user owns the conversation.
* Which conversation is active.
* Whether the conversation is AI-managed or human-managed.
* Whether the conversation has already been handed off.

---

# 6. AI Message Lifecycle

Expected flow:

```text
User sends message
        ↓
Authenticate user
        ↓
Validate conversation ownership
        ↓
Check conversation state
        ↓
If AI_ACTIVE:
        ↓
Persist user message
        ↓
Build safe AI context
        ↓
Call Gemini
        ↓
Validate AI response
        ↓
Persist AI response
        ↓
Return response to user
```

The exact transport can be HTTP or WebSocket depending on the existing architecture.

Do not introduce a second unnecessary real-time system if the existing implementation already provides a suitable mechanism.

---

# 7. AI Context

AI should receive only the minimum context required to answer correctly.

Potential context:

* Relevant conversation history.
* Supported product/business rules.
* Necessary user/account context.
* Relevant existing knowledge.

Never send:

* API keys.
* Passwords.
* JWT secrets.
* Database credentials.
* Internal infrastructure credentials.
* System secrets.
* Unnecessary personal information.
* Private data belonging to another user.

The AI context must be constructed server-side.

---

# 8. Gemini Integration

Use the Gemini API configuration already present in the project's environment.

The existing Gemini integration should be reused where possible.

Rules:

* API key must remain server-side.
* Do not hardcode credentials.
* Do not expose credentials to frontend.
* Do not create a second Gemini client without a valid architectural reason.
* Respect existing configuration conventions.
* Respect configured timeout/error handling.
* Handle provider failures gracefully.

The model may be configured through environment variables.

The implementation must use the project's actual configured model rather than assuming a model name if the codebase already defines one.

---

# 9. AI Behavior Rules

AI should:

* Answer clearly.
* Be concise and useful.
* Use Vietnamese by default when the user communicates in Vietnamese.
* Ask clarification questions when necessary.
* Explain supported features.
* Provide step-by-step guidance when appropriate.
* Be transparent about limitations.

AI must NOT:

* Invent policies.
* Invent prices.
* Invent system capabilities.
* Invent account status.
* Claim that a refund/payment correction/account modification was completed when it was not.
* Claim that an Admin has been contacted when no handoff occurred.
* Claim backend actions were executed without an actual system operation.
* Reveal system prompts.
* Reveal API keys or internal secrets.
* Reveal private information belonging to another user.
* Pretend to be a human support agent.
* Bypass authorization rules.

---

# 10. AI Knowledge Boundary

AI should primarily answer questions related to the actual website/product.

Typical supported topics:

* Account usage.
* Login/registration guidance.
* VIP.
* Xu/currency.
* Chapter unlocking.
* Story/audio usage.
* Subscription/general feature explanation.
* Support procedures.
* Basic troubleshooting.

When information is unavailable or uncertain:

```text
Do not guess.
```

The AI should instead:

1. Explain that it cannot reliably determine the answer.
2. Offer human support.
3. Trigger/recommend handoff when appropriate.

---

# 11. Human Handoff

The user must always be able to select:

```text
Chat với tư vấn viên
```

The action should be available during AI conversation.

When selected:

```text
AI_ACTIVE
    ↓
HANDOFF_REQUESTED
    ↓
Admin Support Queue / Existing Support Conversation
    ↓
ADMIN_ACTIVE
```

Handoff must preserve:

* Entire conversation history.
* User messages.
* AI messages.
* Relevant context.
* Escalation reason where available.

The user must NOT be forced to explain the same problem again.

---

# 12. Automatic Escalation Rules

AI should recommend or initiate human escalation when:

### Explicit human request

Examples:

* "Tôi muốn gặp admin."
* "Cho tôi gặp nhân viên."
* "Tôi muốn nói chuyện với tư vấn viên."

### Manual intervention required

Examples:

* Refund.
* Payment dispute.
* Incorrect transaction.
* Missing purchased entitlement.
* Account restriction.
* Ban/unban.
* Security issue.
* Data correction.
* Manual account changes.

### AI uncertainty

Escalate when AI does not have sufficient reliable information.

### Repeated failure

If the user indicates that AI's answer does not solve the issue repeatedly, AI should offer human support.

### Unsupported request

AI should not attempt unsupported operations.

---

# 13. Handoff Transaction Requirements

Handoff must be treated as a state transition, not merely a UI button.

A correct handoff should approximately perform:

```text
Begin transaction
    ↓
Validate conversation ownership
    ↓
Validate current conversation state
    ↓
Mark handoff requested / human required
    ↓
Prevent future AI ownership
    ↓
Create or reuse admin-support conversation
    ↓
Commit database state
    ↓
Publish notification/event
```

The database should be authoritative.

WebSocket is a delivery mechanism, not the permanent source of truth.

If the notification fails after database commit:

* The conversation must still exist correctly.
* Admin should be able to discover it through normal Support loading.
* The system should support recovery/re-synchronization.

---

# 14. Idempotency

Handoff must be idempotent.

If the user clicks the handoff button multiple times:

```text
First request → performs handoff
Second request → must NOT create another ticket
Third request → must NOT duplicate conversation
```

The same conversation must not receive multiple identical handoff records or notifications unnecessarily.

The implementation should use:

* State validation.
* Database constraints where appropriate.
* Existing conversation uniqueness rules.
* Transaction boundaries.

---

# 15. Race Conditions

The following cases MUST be considered.

## 15.1 AI Response Finishes After Handoff

Example:

```text
User sends message
AI request starts
User presses "Human"
Handoff succeeds
AI request finishes afterwards
```

The outdated AI response MUST NOT be delivered as a normal AI reply after the conversation is already `ADMIN_ACTIVE`.

The system should validate conversation state before committing/delivering the AI response.

## 15.2 Two Handoff Requests

Only one handoff should succeed.

## 15.3 Two Admins

Two admins must not unknowingly take ownership of the same conversation.

Use existing assignment/locking/state mechanisms if available.

## 15.4 Admin Replies During Handoff

The final state must be consistent.

No duplicate conversation should be generated.

## 15.5 WebSocket Disconnect During Handoff

The handoff state must still persist.

When the client reconnects, it must synchronize from server state.

---

# 16. Gemini Failure Handling

Potential failures:

* Timeout.
* Provider unavailable.
* Rate limit.
* Invalid credentials.
* Empty response.
* Unexpected response structure.
* Network failure.
* Server-side exception.

User-facing behavior must be friendly.

Do NOT expose raw Gemini errors.

Recommended behavior:

```text
Hiện tại trợ lý AI đang tạm thời không khả dụng.
Bạn có muốn chuyển sang tư vấn viên không?
```

The conversation itself must not be lost.

---

# 17. AI Rate & Abuse Protection

The system should prevent abusive AI usage.

Analyze and implement according to existing application architecture:

* Per-user request rate limiting.
* Maximum message size.
* Maximum conversation context size.
* Timeout.
* Protection against extremely rapid requests.
* Protection against repeated duplicate submissions.

The implementation must avoid unnecessary load on the Gemini API.

---

# 18. Prompt Injection Protection

Treat user messages as untrusted input.

A user may attempt:

* Requesting the system prompt.
* Asking for internal instructions.
* Asking for API keys.
* Asking to ignore business rules.
* Asking AI to impersonate Admin.
* Asking AI to perform privileged operations.

AI must continue following the application's support rules.

Never insert secrets or privileged instructions into user-visible responses.

---

# 19. Message Ownership

Every message must have an unambiguous source/sender.

Conceptually:

```text
USER
AI
ADMIN
SYSTEM
```

Reuse the existing message sender/type model where possible.

Do not infer sender identity solely from frontend state.

Server-side message ownership is authoritative.

---

# 20. Admin Support Integration

Human escalation MUST integrate into the existing Admin Support system.

When a conversation becomes human-support-required:

* Admin should see it in the existing Support area.
* Existing unread/read rules apply.
* Existing red badge must update.
* Existing WebSocket notification should be reused.
* Existing admin message flow must continue normally.

Do not create an independent "AI Support Admin" dashboard.

---

# 21. Admin Unread Badge

When a user requests human support:

```text
User
 ↓
Handoff
 ↓
Support notification
 ↓
Admin Support tab
 ↓
Red unread badge
```

The badge must:

* Appear when a new human-support message/event requires admin attention.
* Survive page refresh.
* Reflect server state.
* Not depend solely on an in-memory frontend variable.
* Clear/update correctly when the admin reads the relevant conversation.
* Avoid duplicate increments from duplicate WebSocket events.

The server/database remains authoritative for unread state.

---

# 22. WebSocket Rules

The current WebSocket infrastructure should be reused.

Potential events may include concepts such as:

```text
SUPPORT_MESSAGE
SUPPORT_HANDOFF
SUPPORT_UNREAD_UPDATE
SUPPORT_CONVERSATION_UPDATE
```

Exact event names must follow the existing project conventions.

WebSocket responsibilities:

* Real-time notification.
* Real-time support messages.
* Conversation state changes.
* Admin unread updates.

WebSocket MUST NOT be the only mechanism by which state exists.

If an event is missed:

```text
Reconnect
    ↓
Fetch authoritative server state
    ↓
Synchronize conversation/unread state
```

---

# 23. WebSocket Reliability

Handle:

* Disconnect.
* Reconnect.
* Duplicate event.
* Out-of-order event.
* Missed event.
* Browser refresh.
* Multiple tabs.
* Multiple devices.

Messages/events should contain enough information for the client to safely determine whether an event is already processed when required.

---

# 24. Multiple Tabs / Devices

The same user may open Support in:

* Multiple browser tabs.
* Multiple browsers.
* Multiple devices.

The backend must remain authoritative.

A message should not accidentally create multiple conversations because it was submitted from different clients.

Handoff must remain idempotent across clients.

---

# 25. Authentication & Authorization

Every Support operation must verify:

```text
Authenticated user
        +
Conversation ownership
        +
Valid conversation state
```

Users may only access their own conversations.

Admins may only access conversations according to existing Admin authorization rules.

Never trust:

* User-provided conversation IDs.
* Frontend role information.
* Frontend ownership fields.
* Frontend conversation state.

Validate everything server-side.

---

# 26. Persistence Rules

Conversation and important messages must be persisted.

The system should not rely solely on frontend memory.

After:

```text
Refresh
Logout/Login
Reconnect
```

the conversation should remain recoverable according to normal Support retention rules.

AI messages should be distinguishable from human/admin messages.

---

# 27. Existing Conversation Reuse

Before creating a new conversation:

```text
Check for existing active conversation.
```

Avoid:

```text
User opens support
→ new conversation
User refreshes
→ new conversation
User clicks AI again
→ new conversation
```

Duplicate active conversations should not be created accidentally.

Follow the project's current Support conversation lifecycle.

---

# 28. Resolved Conversations

A resolved conversation should not unexpectedly become active merely because a stale browser sends a message.

The backend must validate current state.

If reopening is supported:

* Apply existing reopen rules.

If reopening is not supported:

* Create a new valid support interaction according to the existing business model.

Do not invent behavior silently.

---

# 29. Error Consistency

Backend failures should:

* Produce appropriate HTTP/WebSocket errors.
* Be logged with enough context for debugging.
* Avoid exposing sensitive implementation details.
* Preserve transactional consistency.
* Avoid leaving conversations stuck in invalid states.

---

# 30. Logging

Important events should be observable:

* AI conversation created.
* AI request started.
* AI request failed.
* AI response persisted.
* Handoff requested.
* Handoff committed.
* Admin assignment.
* WebSocket notification failure.
* Conversation resolution.

Do NOT log:

* API keys.
* Passwords.
* JWT secrets.
* Sensitive user data unnecessarily.
* Full private conversation content unless existing logging policy explicitly allows it.

---

# 31. Transaction Boundaries

Database transactions must be used where multiple related state changes must remain consistent.

Example:

```text
handoff state
+
conversation ownership/state
+
required support metadata
```

must not leave the database in a partially updated state.

Do not keep DB transactions open while waiting for long external Gemini/network requests.

Prefer:

```text
Persist required state
    ↓
Call external service outside long DB transaction
    ↓
Persist result
```

according to the actual architecture.

---

# 32. External API Isolation

Gemini is an external dependency.

The Support system must remain stable when Gemini is unavailable.

Gemini failure must NOT:

* Crash the Support system.
* Corrupt conversation state.
* Delete previous messages.
* Prevent human support from functioning.

Users should still be able to contact Admin.

---

# 33. AI Response Ordering

Messages should maintain deterministic ordering.

Consider:

```text
User message A
User message B
```

while AI response A is still processing.

The system must prevent confusing output such as:

```text
AI response B
AI response A
```

if the architecture cannot safely handle concurrent AI generations.

Possible strategies:

* Sequential processing per conversation.
* Message/request identifiers.
* Server-side ordering.
* Conversation-level processing lock.

Choose the approach that best fits the existing system.

---

# 34. User Experience Requirements

The UI should clearly distinguish:

```text
User
AI
Support Agent
```

The user must know whether they are talking to:

* AI.
* Human Support.

During handoff, show a clear transition state.

Example:

```text
Đang kết nối với tư vấn viên...
```

After Admin takes over:

```text
Bạn đang được hỗ trợ bởi tư vấn viên.
```

The actual wording may vary.

---

# 35. No Silent AI Ownership

The system must never silently switch a conversation back from:

```text
ADMIN_ACTIVE
```

to:

```text
AI_ACTIVE
```

unless explicitly designed and authorized.

Human ownership has priority.

---

# 36. No AI After Successful Handoff

Once handoff is committed:

```text
AI must stop automatic responses.
```

Pending asynchronous AI operations must validate current conversation state before persisting/delivering their response.

This rule is mandatory.

---

# 37. Privacy

Only minimum required data should be sent to Gemini.

The system must follow existing application privacy expectations.

Do not expose:

* Another user's conversation.
* Another user's account information.
* Admin-only information.
* Internal system information.

---

# 38. Backward Compatibility

Existing users who do not use AI Support must continue to use the existing Support functionality normally.

Existing:

* Admin chat.
* User chat.
* WebSocket.
* Authentication.
* Unread badge.
* Notifications.
* Conversation history.

must remain functional.

AI must be an extension, not a breaking replacement.

---

# 39. Migration Safety

If database changes are needed:

* Migration must be backward-compatible where possible.
* Existing conversations must remain valid.
* Existing messages must remain readable.
* Existing Admin Support must continue working during/after migration.
* New fields should have safe defaults where appropriate.

Never make assumptions about production data.

---

# 40. Testing Requirements

At minimum, test:

## Normal Flow

```text
User → AI
AI → User
```

## Human Handoff

```text
User → AI
User → Human request
AI → Handoff
Admin notification
Admin → User
```

## Direct Human Support

```text
User → Human
Admin → User
```

## Failure

```text
Gemini timeout
Gemini unavailable
Invalid response
WebSocket disconnect
Database failure
```

## Concurrency

```text
Double handoff
Concurrent messages
Concurrent admin assignment
AI response finishing after handoff
```

## Security

```text
Unauthorized conversation access
Forged conversation ID
Prompt injection
Secret extraction attempts
```

## Recovery

```text
Refresh
Reconnect
Logout/Login
Multiple tabs
Missed WebSocket event
```

---

# 41. Acceptance Criteria

The feature is considered correct only when:

1. Existing Support continues working.
2. User can choose AI support.
3. AI can maintain conversation context.
4. AI uses the existing Gemini configuration.
5. AI errors do not break Support.
6. User can request a human at any time.
7. Human handoff preserves conversation history.
8. Duplicate handoffs do not create duplicate tickets.
9. AI stops responding after successful handoff.
10. Admin receives the handoff through the existing notification/WebSocket architecture.
11. Admin Support unread badge updates correctly.
12. Admin can continue chatting using the existing support flow.
13. Refresh/reconnect does not lose conversation state.
14. Unauthorized users cannot access another user's conversation.
15. Gemini credentials never reach the frontend.
16. Existing Support behavior remains backward-compatible.
17. Race conditions around handoff are handled safely.
18. Production failure scenarios are handled gracefully.

---

# 42. Implementation Principles

Agents implementing this feature MUST follow these principles:

### Principle 1 — Audit Before Code

Understand the actual existing code before modifying it.

### Principle 2 — Reuse Before Create

Reuse existing:

* Support conversations.
* Messages.
* WebSocket.
* Notifications.
* Authentication.
* Gemini integration.

Only create new infrastructure when necessary.

### Principle 3 — Database Is Authoritative

Frontend state and WebSocket events are not the source of truth.

### Principle 4 — Human Support Always Available

AI must never trap the user.

### Principle 5 — No False Claims

AI must never pretend an operation was completed.

### Principle 6 — Handoff Is a State Transition

Handoff must be transactional, idempotent, and race-condition-safe.

### Principle 7 — Security First

Never expose secrets or unauthorized data to users or Gemini.

### Principle 8 — Production Compatibility

The implementation must work with the existing deployed architecture, not just local development.

---

# 43. Implementation Decision Rule

Before introducing any new:

* Table
* Entity
* API
* WebSocket event
* Service
* State
* Queue
* Cache
* External integration

the implementing agent MUST first verify whether the existing architecture already provides an equivalent mechanism.

Prefer the smallest reliable change that satisfies all requirements.

---

# 44. Final Source of Truth

This document defines the intended behavior of the AI Support feature.

If implementation details conflict with this document:

1. Inspect the actual existing architecture.
2. Preserve existing business-critical behavior.
3. Modify this document when the architectural reality requires a design change.
4. Document the reason for the change.
5. Do not silently deviate from these rules.

---

# 45. Implementation Record — What the Architecture Changed

> Added after implementation, as §44 requires. Every entry below is a place
> where the real codebase did not match the shape this document assumed. The
> rule was adapted to the architecture rather than the architecture to the rule,
> and the reason is recorded here.

## 45.1 `RESOLVED` already existed under another name

§3 asks for four modes. Only three were created:

```text
AI  ·  HANDOFF  ·  HUMAN   →  support_conversations.assistant_mode   (new)
RESOLVED                   →  support_conversations.status = CLOSED  (V15)
```

`status` (OPEN/CLOSED/BLOCKED) answers *may you send, and is this handled*.
Mode answers *who owes the reply*. They are orthogonal: `CLOSED` + `AI` is a
real, valid state — an admin closed the case and the reader later asks the
assistant something general. A fourth mode value meaning "resolved" would be a
second column that must always equal the first, which is a second column that
can disagree with the first.

**"Not yet chosen" is also not a stored value.** It is derived from
`lastMessageId == null` — the thread exists but nobody has spoken. No
server-side rule turns on it, and the standing bar in this codebase is that a
state exists only when a business rule changes with it.

## 45.2 Duplicate tickets are not preventable, because they are not expressible

§11 and §14 describe handoff as "create or reuse the admin-support conversation"
and demand that repeated clicks not create a second ticket.

`support_conversations` has `UNIQUE (user_id)` (V15): **one thread per reader,
for the lifetime of the account.** So there is no ticket to create — it already
exists — and no history to copy, because the AI conversation was never stored
anywhere else. Handoff reduces to changing one column.

That makes several requirements of §13 vacuous rather than implemented, which is
the stronger outcome. Idempotency (§14) is still real, and is enforced by
`queueForHuman()` running *inside* the row lock rather than by a flag at the
calling layer — see `SupportConversation` and `SupportConcurrencyTest`.

## 45.3 Message ownership is two columns, not one enum

§19 lists `USER / AI / ADMIN / SYSTEM` as one concept. V15 had already split it,
deliberately, and the split was kept:

```text
sender_role   USER / ADMIN / AI     ← WHO said it
message_type  TEXT / SYSTEM         ← what KIND of thing it is
```

`SYSTEM` is not a sender: a server-authored line still carries the role of the
person who caused it. Putting `SYSTEM` in both enums would create two columns
that must always agree.

`AI` was added to `sender_role`, and AI rows carry `sender_id = NULL` — the
assistant is not a person, and inventing a ghost row in `users` would surface it
in every user list, search box and count. The V15 argument that a nullable
`sender_id` weakens the dedup `UNIQUE` still holds, but does not reach these
rows: assistant replies are keyed by a *derived* id (`ai-<question id>`), which
is stricter than a browser-supplied one. One question yields exactly one answer,
however many times the send is retried.

## 45.4 The badge needed a second branch

§21 says the badge must appear "when a new human-support message/event requires
admin attention". The V15 count — *threads with unread USER TEXT* — misses a
case this feature introduces: a reader clicks **Chat với tư vấn viên** and waits
without typing. No unread message exists, yet someone is plainly waiting.

The count is now:

```text
mode = HANDOFF                      → waiting, always
mode = HUMAN and unread USER TEXT   → waiting  (the V15 rule, unchanged)
mode = AI                           → never
```

The third branch is the whole point of the feature: asking a machine how to
unlock a chapter must not light anyone's lamp.

**One deliberate behaviour change.** Under the old rule, an admin *reading* a
thread cleared the badge even without replying. A `HANDOFF` thread does not
clear that way — it leaves the count when someone actually takes it, which
happens automatically on any admin message or status change
(`takenOverByHuman()`). There is no separate "accept" button, because that is a
step a busy operator can forget, and every forgotten click is a thread stuck in
the count forever.

## 45.5 The assistant suggests escalation; it never performs it

§12 says the AI should "recommend or initiate". It only ever recommends.

Auto-handoff would let a language model create work in a human queue, and would
put the system one bad classification away from violating §9's ban on claiming
an admin was contacted. Instead:

- The model may append an internal marker; the server strips it and sets
  `suggestHandoff`, which makes the button prominent.
- A deterministic phrase check ("tư vấn viên", "gặp admin", …) short-circuits
  *before* Gemini: it costs no quota, never misses the clearest possible
  request, and still works while the provider is down.
- The button is visible regardless (§34, Principle 4).

The reader always performs the transition. Nothing tells them it already
happened when it has not.

## 45.6 An AI turn is HTTP only; WebSocket refuses it

§6 leaves transport open. The socket path cannot carry it: a turn blocks on
Gemini for up to 30s, and holding a socket handler thread that long is not
something this deployment can afford (20 Tomcat threads, 10 DB connections).

So `POST /api/support/ai/messages` is the only entry, and a plain send into an
AI-mode thread is **refused** (`SUPPORT_ASSISTANT_IN_CHARGE`) rather than
silently stored — a question with no answer under it is one the reader waits on
forever. The refusal lives in `SupportService.sendAsUser`, which both REST and
WebSocket pass through.

The answer still reaches other tabs over the existing `message:new` frame. **No
new WebSocket event was added**; none was needed.

## 45.7 Ordering is a per-conversation in-process guard

§33 asks for deterministic ordering. A second concurrent turn on the same thread
is rejected (`SUPPORT_ASSISTANT_BUSY`) rather than queued — queuing parks one
Tomcat thread behind another, and a clear "the assistant is still answering"
beats a chat box sitting silent.

The guard is a `ConcurrentHashMap` key set, so it holds for a single instance,
which is what runs. If this ever scales out the guard tears, and the damage is
bounded and named: one extra quota unit, and two answers possibly out of order.
Nothing about *data* breaks, because what keeps data correct is the row lock in
`SupportStore`, not this set. Same reasoning `SupportRateLimiter` already uses.

## 45.8 Rule 36 is enforced under the row lock, not before the call

The mandatory rule — no AI reply after handoff — cannot be checked before
calling Gemini, because the race is precisely the 30 seconds in between. The
check runs inside the write transaction, after `SELECT … FOR UPDATE`, and a
stale answer is **dropped, not stored-and-hidden**: nobody ever saw it, and an
invisible row would skew every later count. See
`SupportStore.appendAssistantReply`, and the two-thread race in
`SupportConcurrencyTest`.

## 45.9 Quota is a third ledger kind, not a shared counter

`ai_usage.kind` gained `SUPPORT` beside `TTS` and `ASSISTANT`. Sharing the
story-assistant counter would deny someone support because they spent the day
summarising chapters — two unrelated activities, and the second is the road
people take to ask for help. Running out is never a dead end: the human path
costs no quota and passes through none of the assistant's gates.

A failed provider call refunds the unit (`AiUsageService.refundUsage`), recorded
as a second ledger event rather than a deletion — V9's policy.

## 45.10 The welcome text is UI-only; transition markers are persisted

§4 asks for this to be decided explicitly. The greeting and the two buttons are
interface, not conversation: writing them down would put a line nobody said at
the head of every transcript an operator has to scroll through.

Mode transitions *are* persisted as `SYSTEM` messages, because those are what
let an operator read where the machine's part of the transcript began and ended.
Same mechanism `changeStatus` already used for status transitions.

---

## 45.11 One unrelated defect found and fixed

`WalletTransactionRepository.findByUserIdOrderByCreatedAtDesc` ordered by
timestamp alone. Two ledger rows written in the same microsecond tie, and the
database is then free to return them in either order — so a top-up followed
immediately by a purchase could render a reader's ledger inverted, with
`balance_before` / `balance_after` reading as a broken chain.

Renamed to `…OrderByCreatedAtDescIdDesc`. This is the principle V15 already
states for `support_messages`: **order lives in `id`, not in the clock.**

It surfaced because this feature's new test classes shift suite timing. It was
latent before, not introduced.
