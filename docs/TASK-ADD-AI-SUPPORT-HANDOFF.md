You are working on an existing production project with an already implemented user-to-admin Support/Chat system and WebSocket messaging.

DO NOT start coding immediately.

Your first responsibility is to AUDIT, UNDERSTAND, and ANALYZE the existing system completely before proposing or implementing anything.

## Objective

We want to extend the existing Support system with an AI-first support layer while preserving the current admin/user chat architecture.

The intended business direction is:

User opens Support
→ user can choose:

1. `Chat with AI`
2. `Chat with Support Agent`

AI should handle common/general questions for free.

If the user wants a human, or AI cannot reasonably solve the issue:
→ handoff/escalate the conversation to a real admin/support agent
→ preserve the entire conversation/context
→ notify admin through the existing support/WebSocket system
→ admin continues the conversation without forcing the user to repeat the issue.

## PHASE 1 — FULL SYSTEM AUDIT

Before writing any implementation:

### 1. Audit backend architecture

Inspect and understand:

* Support-related entities/models.
* User/admin relationships.
* Conversation/ticket structure.
* Message structure.
* Message ownership/sender types.
* Conversation status/lifecycle.
* Existing unread/read logic.
* Existing notification logic.
* Existing WebSocket implementation.
* Authentication and authorization.
* Service layer.
* Repository/data access layer.
* Controllers/endpoints.
* Existing Gemini integration.
* Existing environment variables/configuration.
* Error handling.
* Transaction boundaries.
* Async/event processing if any.
* Existing scheduled/cleanup logic if any.

Determine exactly how the current system works instead of assuming its architecture.

### 2. Audit frontend architecture

Inspect:

* Support UI.
* Chat UI.
* Admin Support tab.
* Existing red unread badge implementation.
* WebSocket client.
* Connection/reconnection logic.
* State management.
* Message rendering.
* Conversation switching.
* Authentication state.
* API client.
* Existing notification/event handling.

Understand how a message travels from:

User → backend → database → WebSocket → Admin

and:

Admin → backend → database → WebSocket → User.

### 3. Audit production/deployment compatibility

Determine:

* How Gemini is currently configured.
* How environment variables are loaded.
* How WebSocket endpoints are configured.
* How production differs from local development.
* Whether the current architecture is safe for adding an AI layer.
* Whether there are existing limitations that could affect AI streaming, timeout, concurrency, or persistence.

DO NOT change infrastructure during this phase.

---

# PHASE 2 — BUSINESS FLOW ANALYSIS

After understanding the existing architecture, design the complete support flow.

## Flow A — First-time user

Define exactly what should happen when a user opens Support for the first time.

Recommended conceptual flow:

User opens Support
→ system determines there is no active human support conversation
→ show welcome message
→ present:

`Chat with AI`
`Chat with Support Agent`

Determine whether this welcome message should be persisted as a normal message or represented as UI-only content.

Analyze the pros/cons and choose the solution that best fits the existing architecture.

## Flow B — User chooses AI

Define:

* How AI conversation is created.
* Whether AI conversation uses existing conversation/message tables.
* How AI messages are stored.
* How conversation ownership is represented.
* How conversation context is maintained.
* What happens after page refresh.
* What happens after logout/login.
* How the system knows this conversation is currently AI-owned.
* Whether WebSocket is required for AI responses or HTTP is sufficient.
* How AI response latency/timeouts are handled.

## Flow C — User chooses human support

Define:

* Whether a support ticket/conversation is created immediately.
* How duplicate active conversations are prevented.
* How admin is notified.
* How unread state is updated.
* How the existing red badge should behave.
* How the conversation transitions into human handling.

Reuse the existing support architecture whenever possible.

## Flow D — AI → Human Handoff

This is the most important flow.

Design an explicit state transition such as:

AI_ACTIVE
→ HANDOFF_REQUESTED
→ ADMIN_ACTIVE
→ RESOLVED

Analyze whether these states already exist conceptually in the project and whether existing statuses can be reused instead of creating unnecessary new ones.

When handoff happens:

1. Preserve all AI/user messages.
2. Preserve conversation context.
3. Mark the conversation as requiring human support.
4. Prevent further AI responses.
5. Create/reuse the existing admin support conversation.
6. Notify admin through the existing WebSocket/event mechanism.
7. Trigger the existing admin unread notification/badge.
8. Admin opens the conversation and sees the previous AI conversation.
9. Admin replies normally using the existing chat flow.
10. User continues in the same support context.

Analyze race conditions around this transition.

---

# PHASE 3 — AI RESPONSIBILITY & BOUNDARIES

Design clear rules for what AI can and cannot do.

AI SHOULD handle:

* FAQ.
* Website usage guidance.
* Feature explanation.
* General questions about VIP/xu/chapter unlocking/etc.
* Basic troubleshooting.
* Navigation guidance.

AI SHOULD NOT independently perform or falsely claim:

* Refunds.
* Payment correction.
* Account bans/unbans.
* Security-sensitive account operations.
* Manual transaction correction.
* Data changes requiring admin authority.
* Any action that the application does not explicitly expose to AI.

AI must escalate when:

* User explicitly requests an admin.
* User asks for manual intervention.
* Payment/refund issue occurs.
* Account/security issue occurs.
* AI lacks enough information.
* AI repeatedly fails to solve the issue.
* The question is outside the supported knowledge scope.

The user must NEVER be trapped inside AI support.

---

# PHASE 4 — EDGE CASE AUDIT

Thoroughly analyze at minimum:

### Conversation lifecycle

* User starts AI chat.
* User starts human chat immediately.
* User switches from AI to human.
* User switches repeatedly.
* User starts another conversation while one is active.
* Existing unresolved ticket already exists.
* Existing resolved ticket exists.
* Conversation is archived/deleted.
* Admin resolves conversation while user is active.

### AI failures

* Gemini timeout.
* Gemini API unavailable.
* Invalid API key.
* Rate limit.
* Empty response.
* Malformed response.
* AI response takes too long.
* AI returns unsupported/inappropriate output.
* User sends messages rapidly.
* Multiple AI requests execute concurrently.

### Handoff races

Analyze cases such as:

* User requests human twice.
* User requests human while AI request is still processing.
* AI response completes after handoff.
* Two admins accept the same conversation.
* Admin responds while handoff is being processed.
* User reconnects while handoff is occurring.
* WebSocket disconnects during handoff.
* Database transaction succeeds but WebSocket notification fails.
* WebSocket notification succeeds but persistence fails.

Define the correct source of truth and recovery strategy.

### Persistence

* Browser refresh.
* Logout/login.
* Multiple browser tabs.
* Multiple devices.
* Message ordering.
* Duplicate WebSocket events.
* Lost WebSocket events.
* Reconnection.
* Unread count synchronization.

### Security

* User accessing another user's conversation.
* Admin accessing unauthorized conversations.
* Forged conversation IDs.
* Unauthorized handoff.
* Gemini API key exposure.
* Sensitive user data being sent to Gemini.
* Prompt injection attempts.
* User attempting to manipulate AI into revealing system prompts/secrets.
* AI being instructed to perform privileged operations.

### Concurrency

Analyze:

* Duplicate support ticket creation.
* Duplicate handoff.
* Concurrent messages.
* Concurrent admin assignment.
* Concurrent conversation state transitions.
* Transaction isolation.
* Idempotency requirements.

---

# PHASE 5 — DATA MODEL ANALYSIS

Determine whether the current schema can support this feature.

Analyze whether we need:

* Conversation type/mode.
* AI/human ownership.
* Handoff status.
* AI session metadata.
* Escalation reason.
* AI provider/model metadata.
* Message sender/source type.

Do NOT automatically create new tables/fields.

First determine whether existing structures can be reused.

If schema changes are necessary, explain:

* Why.
* Which entity.
* Which field.
* Type.
* Default value.
* Backward compatibility.
* Migration strategy.

---

# PHASE 6 — WEBSOCKET ANALYSIS

Audit the existing WebSocket architecture specifically for this feature.

Determine:

* Which events currently exist.
* Which event should represent AI handoff.
* Which event should update the admin unread badge.
* Whether a dedicated event is required.
* How reconnect synchronization works.
* Whether WebSocket should be the source of truth or only the notification transport.

The database/state must remain authoritative.

WebSocket events must be safe to duplicate or replay.

---

# PHASE 7 — GEMINI INTEGRATION ANALYSIS

The project already contains Gemini configuration in `.env`.

Inspect the existing integration and determine:

* Current Gemini client/service.
* Model configuration.
* Request/response format.
* Error handling.
* Timeout configuration.
* Token/context management.
* Whether conversation history is persisted.
* Whether the current implementation can be reused.

Do not introduce another Gemini integration unless the existing one is genuinely unsuitable.

Do not expose Gemini credentials to the frontend.

---

# PHASE 8 — FINAL ARCHITECTURE DECISION

After the audit, produce a concrete recommended architecture.

Include:

1. Current architecture summary.
2. Existing components that should be reused.
3. Components that need modification.
4. New components required.
5. Database changes.
6. API changes.
7. WebSocket events.
8. State transitions.
9. AI conversation lifecycle.
10. Human handoff lifecycle.
11. Error handling.
12. Security considerations.
13. Concurrency/idempotency strategy.
14. Backward compatibility.
15. Migration strategy.
16. Testing strategy.
17. Edge-case handling.

For every proposed change, explain WHY it is needed.

Prefer the smallest architectural change that fully satisfies the requirements.

---

# PHASE 9 — CREATE THE SPECIFICATION FILE

After completing the audit and architectural analysis, create/update:

`AI_SUPPORT_ASSISTANT_RULES.md`

This file must become the source of truth for the future implementation.

It must contain:

* Product/business purpose.
* User flows.
* AI flow.
* Human support flow.
* AI → human handoff flow.
* Conversation states.
* Message ownership/source rules.
* Escalation rules.
* AI response rules.
* Security/privacy rules.
* Gemini integration rules.
* WebSocket/event rules.
* Error handling.
* Concurrency/idempotency rules.
* Persistence requirements.
* Unread/badge behavior.
* Edge cases.
* Forbidden behaviors.
* Acceptance criteria.
* Testing scenarios.

IMPORTANT:

Do not write generic documentation.

The MD must be based on the ACTUAL codebase discovered during the audit.

If the existing architecture conflicts with the initial product idea, adapt the design to the actual architecture and explicitly document the decision.

---

# FINAL REQUIREMENT

DO NOT IMPLEMENT THE FEATURE YET.

Your output for this task should be the result of:

AUDIT → ANALYZE → DESIGN → DOCUMENT

Only after `AI_SUPPORT_ASSISTANT_RULES.md` is complete should a separate implementation task be executed.

Before finishing, explicitly verify that the proposed design does not unnecessarily duplicate the existing Support, Message, WebSocket, Authentication, Notification, or Gemini infrastructure.
