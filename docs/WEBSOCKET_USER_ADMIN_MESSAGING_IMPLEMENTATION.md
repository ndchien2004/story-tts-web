# Production Task Specification — User ↔ Admin Messaging via WebSocket

## 1. Objective

Implement a complete production-grade realtime messaging system between **Users and Admins** using **WebSocket**.

The system must allow:

* Users to send messages to Admins.
* Admins to receive messages in realtime.
* Admins to reply to Users in realtime.
* All messages to be persisted in the database.
* Users/Admins to retrieve historical messages.
* Unread/read state tracking.
* Conversation lifecycle management.
* Reliable reconnect and missed-message synchronization.
* Duplicate message protection.
* Strong authentication and authorization.
* Safe concurrent processing.
* Correct behavior during network failures, reconnects, server restarts, race conditions, multiple tabs, multiple sessions, and deployment across multiple application instances.

The WebSocket layer must **never be treated as the source of truth**.

The architecture must follow:

```text
Database = Source of Truth
WebSocket = Realtime Delivery Layer
REST/API = Query + Initial Sync + Recovery
```

The final implementation must behave like a real production messaging system rather than a simple demo chat.

---

# 2. Mandatory Initial Analysis

Before writing any code, inspect the entire existing project.

The Agent MUST first understand:

1. Project structure.
2. Backend framework.
3. Frontend framework.
4. Authentication architecture.
5. Authorization/role/permission architecture.
6. Database.
7. ORM/data-access layer.
8. Existing transaction management.
9. Existing exception handling.
10. Existing validation mechanism.
11. Existing WebSocket/event infrastructure.
12. Existing notification system.
13. Existing logging/audit system.
14. Existing pagination conventions.
15. Existing soft-delete conventions.
16. Existing user blocking/locking functionality.
17. Existing admin roles and permissions.
18. Existing deployment architecture.

Inspect existing:

* User entity/model.
* Role/Permission entity/model.
* Admin-related code.
* Authentication/security filters.
* JWT/session handling.
* Controllers.
* Services.
* Repositories.
* Database migrations.
* Transaction boundaries.
* Existing event publishing.
* Existing notification infrastructure.
* Frontend state management.
* Existing HTTP client abstraction.
* Existing WebSocket abstractions, if any.

### Critical Rule

Do NOT immediately create a new architecture.

First determine how the current codebase is structured and integrate messaging into the existing architecture.

Prefer reusing existing:

* authentication
* authorization
* DTO conventions
* validation
* error handling
* transaction management
* event infrastructure
* logging
* configuration
* testing utilities

Do not introduce duplicate mechanisms unless technically necessary.

---

# 3. Core Architectural Principles

The system must follow these principles:

```text
Server is authoritative.
Database is authoritative.
Client is untrusted.
WebSocket is transport, not persistence.
REST is not a replacement for realtime delivery.
Realtime delivery must never be the only copy of a message.
```

The system must NOT use this unreliable flow:

```text
Client
  ↓
WebSocket
  ↓
Broadcast
  ↓
Done
```

Instead:

```text
Client
  ↓
Authenticate
  ↓
Authorize
  ↓
Validate
  ↓
Idempotency check
  ↓
Database transaction
  ↓
Persist message
  ↓
Update conversation state
  ↓
Commit
  ↓
Publish realtime event
  ↓
ACK sender
```

This guarantees that a message is recoverable even if realtime delivery fails.

---

# 4. Domain Model

The messaging domain should contain at least:

```text
Conversation
Message
Conversation/User Read State
```

The exact naming must follow existing project conventions.

---

# 5. Conversation Model

A Conversation represents a messaging thread between a User and the support/admin side.

Potential fields:

```text
id
userId
status
createdAt
updatedAt
lastMessageId
lastMessageAt
createdBy
closedAt
closedBy
version
```

Only include fields that are actually required.

Possible statuses:

```text
OPEN
CLOSED
BLOCKED
ARCHIVED
```

Do not blindly add all statuses.

The Agent must identify the actual business requirement first.

## Conversation Uniqueness

If the business model requires one active conversation per user:

The system must guarantee that concurrent requests cannot create duplicate active conversations.

Example race:

```text
Request A → create conversation
Request B → create conversation
```

Both must NOT successfully create separate active conversations for the same user if business rules allow only one.

Use appropriate:

* unique constraints
* transaction isolation
* locking
* atomic create-or-get logic

based on the existing architecture.

---

# 6. Message Model

A Message should contain at minimum:

```text
id
conversationId
senderId
senderType / senderRole
content
messageType
status
createdAt
updatedAt
clientMessageId
deletedAt
version
```

Possible message types:

```text
TEXT
SYSTEM
```

Do not implement attachments unless they are actually required by the current scope.

Do not store binary files directly in WebSocket payloads.

---

# 7. Client Message ID and Idempotency

This is a mandatory requirement.

Every client-generated message must contain a unique:

```text
clientMessageId
```

Prefer UUID or the project's equivalent stable identifier.

Example:

```text
User sends message
↓
Server persists successfully
↓
Server sends ACK
↓
Network fails
↓
Client never receives ACK
↓
Client retries
```

Without idempotency:

```text
Hello
Hello
```

would be stored twice.

The system must guarantee:

```text
same conversation
+
same sender
+
same clientMessageId
=
one logical message
```

Prefer enforcing uniqueness at the database level where appropriate.

For example:

```text
UNIQUE(conversation_id, sender_id, client_message_id)
```

Do NOT rely only on in-memory duplicate detection.

---

# 8. Message Send Flow

The official server-side flow must be:

```text
Client
  ↓
WebSocket SEND
  ↓
Authenticate connection/session
  ↓
Authorize sender
  ↓
Validate payload
  ↓
Validate conversation access
  ↓
Check idempotency
  ↓
BEGIN TRANSACTION
  ↓
Persist Message
  ↓
Update Conversation metadata
  ↓
Commit
  ↓
Publish realtime event
  ↓
Send ACK
```

The Agent must explicitly define transaction boundaries.

---

# 9. Transaction Requirements

All logically atomic database changes must be handled transactionally.

Example:

```text
Message INSERT
+
Conversation.lastMessageId update
+
Conversation.lastMessageAt update
```

If these operations must be consistent, they must belong to the same transaction.

Do NOT create this anti-pattern:

```text
BEGIN TRANSACTION
↓
Database update
↓
Wait for WebSocket/network
↓
COMMIT
```

Never keep database transactions open while waiting for:

* WebSocket delivery
* client response
* network operations
* external services

Correct:

```text
BEGIN
↓
DB operations
↓
COMMIT

then

realtime publish
```

---

# 10. Transactional Outbox Evaluation

The Agent must evaluate whether the current architecture requires a **Transactional Outbox**.

Ideal architecture:

```text
DB Transaction
    |
    +---- Message
    |
    +---- Conversation update
    |
    +---- Outbox Event
             |
             ↓
        Event Publisher
             |
             ↓
          WebSocket
```

This prevents:

```text
DB commit succeeds
↓
WebSocket publish fails
```

from creating an unrecoverable realtime delivery gap.

If the current system does not require Outbox because of project scale and deployment architecture, document the reasoning.

However, the implementation must still guarantee recovery through database history and reconnect synchronization.

---

# 11. WebSocket Authentication

WebSocket connections must be authenticated.

Never assume:

```text
WebSocket Connected = Authenticated
```

The Agent must integrate with the existing authentication system.

Inspect whether the application uses:

* JWT
* Access tokens
* Cookies
* Sessions
* OAuth
* Security filters
* Custom authentication middleware

Do not create a second independent authentication system unless absolutely necessary.

---

# 12. Token and Session Expiration

Handle all of the following:

### Valid token

Connection is accepted.

### Expired token while connected

The system must not blindly keep the connection alive forever.

Implement behavior compatible with the current security architecture:

```text
disconnect
re-authenticate
refresh
```

as appropriate.

### User logout

If logout invalidates the authentication state, the related WebSocket connection must no longer be allowed to perform privileged actions.

### User account lock

If an Admin locks a User:

```text
Admin LOCK USER
↓
Existing WebSocket session must be invalidated/terminated
↓
Future messaging actions must be rejected
```

A previously established WebSocket connection must never bypass account lock rules.

---

# 13. Authorization

A User may only:

```text
read their own conversation
send messages to their own conversation
mark their own conversation/messages as read
```

A User must never be able to access another User's conversation by modifying:

```text
conversationId
userId
senderId
```

Do not trust a user-provided `userId`.

Derive ownership from the authenticated principal.

Example:

```text
authenticatedUser.id
        ↓
conversation.userId
        ↓
must match
```

---

# 14. Admin Authorization

Admin access must be explicitly authorized.

Do not assume that every administrative account automatically has unrestricted messaging access.

Inspect the existing:

* roles
* permissions
* support roles
* moderation roles
* super-admin roles

If the project uses permission-based access control, prefer permissions instead of hard-coded role checks.

The Agent must determine:

```text
Who can view conversations?
Who can reply?
Who can close conversations?
Who can reopen them?
Who can view restricted conversations?
```

Do not invent a new permission system if one already exists.

---

# 15. Conversation Access Control

Every access path must enforce ownership/permission rules.

This applies to:

* REST APIs
* WebSocket events
* history loading
* unread count
* read state
* status changes
* message operations
* admin inbox
* future attachments

Do not rely on frontend route guards or UI restrictions.

The server must always enforce access control.

---

# 16. Message Validation

Server-side validation is mandatory.

Validate:

```text
content != null
content != blank
max message length
message type
conversation exists
conversation status
sender permission
```

Do not trust these values from the client:

```text
senderId
senderRole
createdAt
status
read state
```

These must be server-generated or server-validated.

---

# 17. Content Security

User-generated messages are untrusted input.

Protect against:

* XSS
* HTML injection
* script injection
* malformed payloads
* excessively large content
* malicious control characters if relevant

Do not render raw user-provided HTML unless an explicit, secure sanitization policy exists.

Prefer safe text rendering.

---

# 18. Rate Limiting

Messaging must be rate-limited server-side.

Protect against:

```text
1000 messages per second
```

or similar abuse.

The Agent must inspect existing rate-limiting infrastructure.

Possible dimensions:

```text
per user
per connection
per conversation
per IP
```

Do not rely only on frontend throttling.

When a rate limit is exceeded:

```text
Reject request
Do not persist message
Return stable error code
```

---

# 19. Message Size Limit

Messages must have a configurable maximum size.

Do not hard-code arbitrary values throughout the code.

Use existing configuration conventions:

```text
application.yml
application.properties
environment variables
config classes
```

or whatever is already used by the project.

---

# 20. WebSocket Event Contract

Define a clear and versionable event contract.

Possible client → server events:

```text
conversation:join
message:send
message:read
conversation:close
conversation:reopen
typing:start
typing:stop
```

Only implement events required by the current scope.

Possible server → client events:

```text
message:new
message:ack
message:error
message:read
conversation:updated
typing:start
typing:stop
system:error
```

All payloads must use DTOs.

Do not serialize internal database entities directly.

Do not expose internal fields unintentionally.

---

# 21. ACK Contract

Every message send must have an explicit ACK mechanism.

The ACK should contain enough information to reconcile optimistic client state, such as:

```text
clientMessageId
serverMessageId
status
serverTimestamp
```

Possible statuses:

```text
ACCEPTED
DUPLICATE
REJECTED
```

The exact names should follow the project's convention.

ACK semantics must be documented.

---

# 22. Delivery Status

If the business requirement requires Messenger-like states, use meaningful state distinctions:

```text
SENT
DELIVERED
READ
FAILED
```

Do not assume:

```text
SENT = DELIVERED = READ
```

Definitions:

### SENT

The server successfully persisted the message.

### DELIVERED

The recipient connection has received/handoffed the event according to the system's delivery model.

### READ

The recipient explicitly read/acknowledged the message.

Do not mark a message as READ merely because the WebSocket event was delivered.

---

# 23. Read Receipts

When a recipient marks messages as read:

```text
message:read
```

the server must:

1. Authenticate.
2. Authorize.
3. Validate conversation ownership.
4. Verify the target message belongs to the conversation.
5. Update read state.
6. Commit state.
7. Publish read receipt.
8. Update unread state.

A User must never be allowed to mark another User's messages as read.

---

# 24. Unread Count

Unread state must be authoritative on the server.

Do not rely exclusively on frontend counters.

The Agent should consider storing state such as:

```text
conversationId
userId
lastReadMessageId
lastReadAt
```

A monotonic `lastReadMessageId` or equivalent is generally safer than repeatedly decrementing counters under high concurrency.

The implementation must correctly handle:

```text
Message A created
Message B created
Read action
Message C created
```

without incorrectly marking C as read.

---

# 25. Initial Sync

Opening a conversation should support:

```text
REST API
↓
Conversation state
↓
Latest messages
↓
WebSocket realtime updates
```

The implementation must handle race conditions between:

```text
REST history request
WebSocket subscription
New message arriving
```

Do not allow a message to disappear in the synchronization window.

A robust synchronization protocol must be designed using an appropriate:

* event ID
* message ID
* sequence number
* cursor
* server version

according to the current architecture.

---

# 26. Reconnect and Missed Messages

Reconnect is mandatory.

Example:

```text
User connected
↓
Network disconnect
↓
Admin sends 3 messages
↓
User reconnects
```

The User must receive all messages that were missed.

Do not simply reconnect the socket and assume the latest state is enough.

A recommended flow:

```text
CONNECT
↓
AUTHENTICATE
↓
SYNC from last known message/event/sequence
↓
Receive missed messages
↓
Enter realtime state
```

The implementation must be resilient to partial failure.

---

# 27. Message Ordering

Do not trust client timestamps for ordering.

The server must provide authoritative ordering.

Possible mechanisms:

```text
database sequence
auto-increment ID
server-generated sequence
event sequence
```

The exact mechanism must match the project's database and architecture.

Client clocks must never determine authoritative message ordering.

---

# 28. Duplicate Event Handling

Duplicate events may occur because of:

* reconnect
* retries
* multiple tabs
* server retry
* event replay
* network instability

Frontend message state must be idempotent.

Example:

```text
message.id already exists
↓
do not render it twice
```

Backend message persistence must also be idempotent.

Never assume exactly-once delivery.

---

# 29. Multiple Tabs and Sessions

Users may have:

```text
Browser tab 1
Browser tab 2
Mobile browser
Desktop browser
```

The system must distinguish:

```text
User identity
```

from:

```text
WebSocket connection identity
```

Multiple connections must not create duplicate database messages.

If one session sends a message:

```text
Tab 1 sends message
```

the system should correctly determine which other sessions must receive the event.

---

# 30. Multiple Admin Sessions

An Admin may have multiple active sessions.

Example:

```text
Desktop
Laptop
Browser tab 1
Browser tab 2
```

Do not create duplicate messages merely because multiple connections exist.

The system must maintain:

```text
one logical message
multiple possible delivery sessions
```

---

# 31. Offline Admin

When no Admin is online:

```text
User sends message
```

the message must still be persisted.

When an Admin reconnects:

```text
Admin connects
↓
Unread/pending conversation state becomes available
```

No message may depend on an active Admin WebSocket connection.

---

# 32. Offline User

If the User is offline:

```text
Admin sends message
↓
Persist message
```

When the User reconnects:

```text
Sync missing messages
```

must recover all relevant messages.

---

# 33. Server Restart

Test:

```text
User connected
Admin connected
Messages exchanged
Server restarts
```

After restart:

* messages must still exist;
* history must remain correct;
* unread state must remain correct;
* clients must reconnect;
* missed messages must synchronize;
* no duplicate messages may be created.

No critical messaging state should depend only on process memory.

---

# 34. Multi-Instance Deployment

The Agent MUST inspect the actual deployment architecture.

If the application can run:

```text
Instance A
Instance B
Instance C
```

then this scenario must work:

```text
User → Instance A
Admin → Instance B
```

Local in-memory broadcasting alone is insufficient.

The Agent must evaluate whether the project requires:

```text
Redis Pub/Sub
Redis Streams
Message Broker
Shared Event Bus
WebSocket Gateway
Sticky Sessions
```

Do not add infrastructure blindly.

Choose the simplest architecture that correctly supports the project's deployment model.

---

# 35. Load Balancer / Proxy Requirements

If WebSocket is deployed behind:

* Nginx
* Load Balancer
* Reverse Proxy
* Cloud platform
* Kubernetes ingress

verify:

```text
WebSocket upgrade
connection timeout
idle timeout
proxy timeout
sticky sessions if needed
forwarded headers
authentication headers
```

Do not assume the infrastructure supports WebSocket correctly.

---

# 36. Heartbeat

WebSocket connections should have a heartbeat/ping-pong mechanism or equivalent.

Goals:

* detect dead connections;
* avoid zombie sessions;
* clean stale connections;
* trigger reconnect;
* release server resources.

Do not keep dead connections indefinitely.

---

# 37. Connection Limits

Protect the server from excessive connections.

Consider limits such as:

```text
connections per user
connections per IP
global connection limit
```

The exact limits should be configurable.

---

# 38. Disconnect Cleanup

When a WebSocket disconnects:

* remove stale connection state;
* unsubscribe listeners;
* release resources;
* prevent memory leaks;
* prevent stale presence state.

A closed socket must not remain referenced indefinitely.

---

# 39. Logging

Use structured logging according to the project's existing logging system.

Log important events such as:

```text
WEBSOCKET_CONNECTED
WEBSOCKET_AUTH_FAILED
WEBSOCKET_DISCONNECTED
MESSAGE_RECEIVED
MESSAGE_PERSISTED
MESSAGE_DUPLICATE
MESSAGE_REJECTED
MESSAGE_DELIVERED
MESSAGE_READ
RATE_LIMITED
```

Never log:

```text
JWT
password
session secrets
private keys
sensitive authentication material
```

Avoid logging full message content unless explicitly required by an approved debugging/audit policy.

---

# 40. Audit Logging

Security-sensitive actions should be auditable when the project already supports audit logging.

Examples:

```text
conversation closed
conversation reopened
admin reply
message deleted
user blocked
permission violation
```

Reuse the existing audit system.

Do not create a second independent audit framework.

---

# 41. Error Handling

WebSocket errors must use stable structured error codes.

Example:

```json
{
  "type": "message:error",
  "code": "CONVERSATION_ACCESS_DENIED",
  "message": "You do not have permission to access this conversation.",
  "clientMessageId": "..."
}
```

Never expose:

* SQL errors
* stack traces
* internal class names
* database schema
* infrastructure details

to clients.

---

# 42. Security Edge Cases

The Agent must explicitly test all of the following.

### Case 1 — Unauthorized conversation access

User A requests User B's conversation.

Expected:

```text
DENIED
```

### Case 2 — Sender spoofing

Client sends:

```text
senderId = adminId
```

Expected:

```text
Server ignores client value
Server derives sender from authenticated principal
```

### Case 3 — Role spoofing

Client sends:

```text
senderRole = ADMIN
```

Expected:

```text
Server ignores/rejects
```

### Case 4 — Duplicate message

Same:

```text
conversationId
senderId
clientMessageId
```

sent twice.

Expected:

```text
one logical message
```

### Case 5 — Locked user

User is locked while connected.

Expected:

```text
WebSocket invalidated/terminated
future message rejected
```

### Case 6 — Logout

User logs out while WebSocket remains open.

Expected:

```text
old authenticated session cannot continue privileged actions
```

### Case 7 — Expired authentication

Expected:

```text
authentication policy enforced
```

### Case 8 — Closed conversation

User sends after conversation is closed.

The business rule must explicitly define whether:

```text
REJECT
```

or:

```text
REOPEN
```

is correct.

Do not make the decision only on frontend.

### Case 9 — Permission revocation

Admin loses permission while connected.

New privileged actions must be denied.

### Case 10 — Resource abuse

Oversized or excessively frequent payloads are rejected.

---

# 43. Race Conditions

The Agent must actively test concurrent scenarios.

### Concurrent messages

```text
User sends A
Admin sends B
at the same time
```

Ordering must remain deterministic.

### Concurrent read/write

```text
Admin marks read
User sends new message
```

The new message must not accidentally become READ.

### Concurrent close/send

```text
Admin closes conversation
User sends message
```

A deterministic business rule must be enforced transactionally.

### Concurrent conversation creation

Two requests must not create duplicate active conversations if the business rule forbids them.

### Multiple admins replying

If multiple admins can respond simultaneously, the resulting messages must remain independent and consistent.

---

# 44. Pagination

Never load unlimited message history.

Prefer cursor-based pagination for chat history.

Example:

```text
GET /conversations/{id}/messages?before=<cursor>&limit=50
```

The exact endpoint must follow project conventions.

Support:

```text
load latest messages
scroll upward
load older messages
```

Avoid offset pagination for large chat histories unless there is a strong reason.

---

# 45. Conversation List for Admins

The Admin inbox should support:

```text
conversation list
latest message
unread count
last activity
user summary
conversation status
```

Sorting should be server-side.

For example:

```text
updatedAt DESC
```

Do not rely on client-side sorting with untrusted timestamps.

---

# 46. Frontend WebSocket State

The frontend should explicitly model:

```text
DISCONNECTED
CONNECTING
CONNECTED
RECONNECTING
ERROR
```

Message state may include:

```text
PENDING
SENT
DELIVERED
READ
FAILED
```

The UI must not treat every WebSocket send as automatically successful.

---

# 47. Optimistic UI

Optimistic rendering is allowed.

Example:

```text
User sends message
↓
Render message as PENDING
↓
Server ACK
↓
Convert to SENT
```

The optimistic message must use:

```text
clientMessageId
```

When ACK arrives, update the existing message rather than appending a duplicate.

If the send fails:

```text
status = FAILED
```

and the user should be able to retry.

Retry must preserve the same `clientMessageId`.

---

# 48. Retry Strategy

Do not retry infinitely.

Use:

```text
exponential backoff
maximum retry count
jitter
```

Connection retries and message retries must be controlled separately where appropriate.

Retries must remain idempotent.

---

# 49. Typing Indicator

Typing indicators are optional unless required.

If implemented:

* do not persist typing events as messages;
* throttle/debounce keypress events;
* use expiration/timeout;
* do not create database records;
* do not allow stale typing indicators forever.

Example:

```text
typing:start
typing:stop
```

with a safe timeout fallback.

---

# 50. Presence

If the UI displays User/Admin online state, define it correctly.

A TCP/WebSocket connection does not automatically prove that a human is actively using the application.

Possible state:

```text
ONLINE
OFFLINE
LAST_SEEN
```

Use heartbeat/session activity as appropriate.

Do not over-engineer presence if it is not required.

---

# 51. Database Indexing

Review indexes according to actual query patterns.

Potential indexes:

```text
conversation.user_id
message.conversation_id
message.created_at
message.conversation_id + message.created_at
clientMessageId uniqueness
```

Do not blindly create every possible index.

Every index must have a query/performance justification.

---

# 52. Soft Delete

If the project uses soft deletion, messaging must follow the same policy.

The Agent must explicitly define:

```text
Can deleted messages still appear?
Can Admins see deleted messages?
Can users see placeholders?
Should deleted content remain auditable?
```

Do not introduce hard deletion if the existing application requires audit/history retention.

---

# 53. Message Edit/Delete

Do NOT implement message edit/delete unless explicitly required by the business scope.

If already required, correctly implement:

```text
authorization
audit
updatedAt
realtime update
state transitions
```

---

# 54. User Deletion

If a User is deleted, do not blindly cascade-delete all conversations/messages.

Inspect existing user deletion policies:

```text
soft delete
hard delete
anonymization
retention
audit
```

Messaging data must follow the existing business/data retention model.

---

# 55. User Lock / Block

If an account is locked or blocked:

```text
existing WebSocket → invalidate
new WebSocket → reject
new message → reject
history access → follow business policy
```

Admins should retain access when allowed by the support/moderation policy.

---

# 56. Admin Deactivation

If an Admin is disabled or loses messaging permission:

```text
existing connection → invalidate if required
new message actions → reject
conversation access → re-authorize
```

Do not allow a stale connection to preserve revoked privileges.

---

# 57. Client Trust Model

This is a hard requirement:

```text
Anything coming from the client is untrusted.
```

Never trust client-provided:

```text
senderId
role
permission
status
createdAt
read state
conversation ownership
```

The server must derive or verify all authoritative information.

---

# 58. Timestamp Rules

Client timestamps must not be authoritative.

Use server-generated:

```text
createdAt
updatedAt
```

preferably in UTC according to existing backend/database conventions.

Frontend should convert timestamps to local timezone for display.

---

# 59. Message State Machine

If message states are implemented:

```text
PENDING
SENT
DELIVERED
READ
FAILED
```

define valid transitions.

Example:

```text
PENDING → SENT
SENT → DELIVERED
DELIVERED → READ

PENDING → FAILED
SENT → FAILED
```

Do not allow arbitrary client-driven state changes.

The server controls message state.

---

# 60. Conversation State Machine

Define valid conversation state transitions.

For example:

```text
OPEN → CLOSED
CLOSED → OPEN
OPEN → BLOCKED
```

The exact state machine must match business rules.

Only authorized actors may trigger transitions.

Clients cannot arbitrarily assign conversation status.

---

# 61. Data Retention

Do not automatically delete messages unless the existing system already defines retention behavior.

If no retention policy exists:

* preserve messages according to current database policy;
* do not add unexpected scheduled deletion;
* document the assumption.

---

# 62. Privacy

Only expose fields required by the messaging UI.

Do not return internal:

* moderation metadata
* security metadata
* admin-only information
* unrelated User information

Use dedicated response DTOs.

Never serialize database entities directly to clients unless the existing architecture explicitly does so safely.

---

# 63. REST API Design

Follow existing API naming conventions.

Possible endpoints:

```text
GET    /conversations
GET    /conversations/{id}
GET    /conversations/{id}/messages
POST   /conversations
PATCH  /conversations/{id}/read
PATCH  /conversations/{id}/status
```

These are examples only.

Use the project's existing conventions.

Do not duplicate existing APIs.

---

# 64. WebSocket Authorization vs Connection Authentication

These are two separate concerns.

At connection:

```text
Who are you?
```

For every operation:

```text
Are you allowed to perform this action?
```

Do NOT assume:

```text
Authenticated once
=
Authorized forever
```

Authorization must be revalidated for sensitive operations.

---

# 65. Performance Requirements

The implementation should:

* avoid loading entire conversation history;
* avoid N+1 queries;
* avoid broadcasting unnecessary payloads;
* avoid serializing database entities directly;
* keep WebSocket handlers lightweight;
* avoid blocking operations inside WebSocket threads when possible;
* avoid long transactions;
* avoid unbounded in-memory message queues.

The Agent must inspect transaction pool usage and ensure messaging does not unnecessarily consume or hold database connections.

---

# 66. Connection Pool Safety

Because the project has previously experienced transaction/database pool concerns, messaging implementation MUST explicitly verify:

* WebSocket handlers do not hold DB connections unnecessarily.
* Long-running connections do not occupy DB connections.
* Transactions are short-lived.
* No database transaction is opened for the entire WebSocket lifecycle.
* Reconnect storms do not exhaust the connection pool.
* Message bursts do not create uncontrolled database pressure.
* Connection/session objects do not leak resources.

Never associate one database transaction or connection with a persistent WebSocket connection.

---

# 67. Deployment Considerations

The Agent must verify compatibility with the project's actual runtime/deployment environment.

Check:

```text
WebSocket endpoint routing
reverse proxy
TLS
load balancer
timeouts
sticky sessions
horizontal scaling
Redis/Broker requirements
environment configuration
CORS
allowed origins
```

Do not assume local-development configuration is production-safe.

---

# 68. Configuration

Move all tunable parameters to configuration.

Examples:

```text
max message length
heartbeat interval
connection timeout
rate limit
retry count
pagination size
reconnect delay
```

Do not scatter magic numbers across the codebase.

---

# 69. Testing Requirements

The Agent MUST create tests for:

## Unit Tests

* message validation
* authorization
* conversation ownership
* idempotency
* unread logic
* read logic
* state transitions
* blocked user behavior
* permission validation

## Integration Tests

* WebSocket authentication
* WebSocket authorization
* message persistence
* transaction behavior
* REST history
* WebSocket + REST synchronization
* reconnect recovery
* duplicate message handling

## Concurrency Tests

* simultaneous messages
* concurrent conversation creation
* concurrent read/send
* close/send race
* duplicate retries

## Security Tests

* IDOR
* sender spoofing
* role spoofing
* unauthorized conversation access
* locked user
* expired token
* revoked permission
* payload abuse
* rate limit bypass

---

# 70. Mandatory Test Matrix

The Agent must test at least:

| Scenario                                  | Expected Result                                        |
| ----------------------------------------- | ------------------------------------------------------ |
| User online sends message                 | Success                                                |
| User offline sends message                | Message persisted                                      |
| Admin offline                             | Message persisted                                      |
| User reconnects                           | Missed messages synchronized                           |
| Duplicate clientMessageId                 | No duplicate logical message                           |
| User accesses another user's conversation | Denied                                                 |
| User spoofs senderId                      | Ignored/rejected                                       |
| User spoofs admin role                    | Ignored/rejected                                       |
| Locked user sends message                 | Denied                                                 |
| Token expired                             | Rejected according to security policy                  |
| Admin permission revoked                  | New privileged action denied                           |
| Closed conversation                       | Business rule enforced                                 |
| Server restart                            | Data preserved                                         |
| Multiple tabs                             | No logical duplicates                                  |
| Multiple admin sessions                   | No duplicate persistence                               |
| Simultaneous messages                     | Deterministic ordering                                 |
| Concurrent read/send                      | Correct unread state                                   |
| Rate limit exceeded                       | Rejected                                               |
| Oversized message                         | Rejected                                               |
| Invalid message type                      | Rejected                                               |
| Invalid conversation                      | Rejected                                               |
| Database failure                          | No false-success ACK                                   |
| WebSocket publish failure                 | Message remains recoverable                            |
| Multi-instance deployment                 | Cross-instance delivery works or documented limitation |
| Reconnect after partial failure           | No message loss                                        |
| Connection leak test                      | Resources cleaned up                                   |

---

# 71. Observability

If monitoring infrastructure exists, add meaningful metrics:

```text
active websocket connections
connections by role
connection failure rate
disconnect rate
authentication failure rate
message throughput
message persistence latency
message delivery latency
duplicate message rate
reconnect rate
unread update errors
WebSocket error rate
```

These metrics should help detect:

* connection leaks
* message loss
* duplicate delivery
* database pressure
* reconnect storms
* latency spikes

Do not collect sensitive message content as metrics.

---

# 72. Documentation

After implementation, update the appropriate documentation.

Document:

```text
WebSocket endpoint
authentication mechanism
authorization rules
event names
payload schemas
ACK semantics
error codes
reconnect strategy
message synchronization
REST APIs
database migration
configuration
production deployment requirements
```

If the project uses:

* OpenAPI
* Swagger
* API docs
* architecture docs

update them appropriately.

---

# 73. Implementation Workflow

The Agent MUST work in this order:

```text
STEP 1
Inspect repository

STEP 2
Map existing architecture

STEP 3
Understand authentication

STEP 4
Understand authorization

STEP 5
Understand database and transaction management

STEP 6
Inspect existing WebSocket/event infrastructure

STEP 7
Design messaging domain

STEP 8
Design database schema/migrations

STEP 9
Design REST contract

STEP 10
Design WebSocket contract

STEP 11
Implement backend

STEP 12
Implement frontend

STEP 13
Implement idempotency

STEP 14
Implement reconnect/recovery

STEP 15
Implement security enforcement

STEP 16
Implement unread/read state

STEP 17
Implement rate limits and payload limits

STEP 18
Implement tests

STEP 19
Run build

STEP 20
Run complete test suite

STEP 21
Review race conditions

STEP 22
Review security

STEP 23
Review transaction/resource usage

STEP 24
Review deployment/scaling implications

STEP 25
Update documentation

STEP 26
Perform final production-readiness review
```

Do not skip directly to coding.

---

# 74. Final Security Review

Before declaring the task complete, answer:

```text
Can User A read User B's conversation?

Can User A send to User B's conversation?

Can User spoof senderId?

Can User spoof role?

Can User spoof message status?

Can a locked user continue through an existing WebSocket?

Can a logged-out user continue using an old connection?

Can an expired authentication state continue indefinitely?

Can a revoked Admin permission remain active?

Can REST APIs bypass WebSocket authorization?

Can WebSocket events bypass REST authorization?

Can a user mark another user's messages as read?

Can duplicate messages be created?

Can duplicate events create duplicate UI messages?

Can reconnect cause missing messages?

Can reconnect cause duplicated messages?

Can client timestamps manipulate message ordering?

Can multiple concurrent requests create duplicate conversations?

Can close/send races produce invalid state?

Can read/send races corrupt unread state?

Can a database failure produce a false success ACK?

Can WebSocket delivery failure cause permanent data loss?

Can server restart lose messages?

Can multi-instance deployment lose events?

Can multiple tabs create duplicate logical messages?

Can one user create too many connections?

Can one user spam messages?

Can oversized payloads consume excessive resources?

Can stale WebSocket connections leak memory?

Can WebSocket handlers exhaust the transaction pool?

Can logging expose private message data?

Can malicious payloads cause XSS?

Can unauthorized users enumerate conversation IDs?

Can internal database fields leak through DTOs?
```

If any answer is:

```text
YES
UNKNOWN
MAYBE
```

the Agent must investigate and fix/document the issue before completion.

---

# 75. Definition of Done

The feature is considered DONE only when:

```text
[ ] User ↔ Admin realtime messaging works
[ ] Messages are persisted reliably
[ ] Authentication is enforced
[ ] Authorization is enforced
[ ] Conversation ownership is enforced
[ ] Client identity spoofing is prevented
[ ] Idempotency is implemented
[ ] Duplicate persistence is prevented
[ ] Duplicate UI rendering is prevented
[ ] Reconnect is implemented
[ ] Missed-message synchronization is implemented
[ ] Message ordering is authoritative
[ ] Unread state is correct
[ ] Read receipt is correct
[ ] Locked users are blocked
[ ] Logged-out sessions cannot continue privileged messaging
[ ] Revoked permissions are enforced
[ ] Rate limiting is implemented
[ ] Payload validation is implemented
[ ] Transaction boundaries are reviewed
[ ] Database connection usage is safe
[ ] Connection pool usage is safe
[ ] Race conditions are reviewed
[ ] Multi-tab behavior is reviewed
[ ] Multi-session behavior is reviewed
[ ] Server restart behavior is reviewed
[ ] Multi-instance deployment is reviewed
[ ] WebSocket proxy/load-balancer behavior is reviewed
[ ] Error responses are standardized
[ ] Sensitive information is not leaked
[ ] Structured logging is implemented
[ ] Tests are implemented
[ ] Security tests pass
[ ] Concurrency tests pass
[ ] Build passes
[ ] Full test suite passes
[ ] Existing functionality remains unaffected
[ ] Documentation is updated
[ ] Production-readiness review is completed
```

---

# 76. Non-Negotiable Agent Rules

DO NOT:

* implement only the happy path;
* build a demo-level WebSocket chat;
* store messages only in memory;
* trust client-provided identity;
* trust client-provided roles;
* trust client-provided permissions;
* broadcast before persistence;
* keep database transactions open during WebSocket/network operations;
* use client timestamps as authoritative ordering;
* ignore reconnect;
* ignore duplicate messages;
* ignore race conditions;
* ignore token expiration;
* ignore user locking;
* ignore permission revocation;
* ignore multiple browser tabs;
* ignore multiple sessions;
* ignore horizontal scaling;
* ignore transaction pool/resource usage;
* introduce unnecessary dependencies;
* create duplicate authentication systems;
* bypass existing project conventions;
* modify unrelated business features;
* expose internal errors;
* expose secrets;
* expose private message content through logs;
* declare the task complete just because messages appear realtime.

The goal is NOT:

```text
"WebSocket chat is working."
```

The goal is:

```text
"Messaging is production-ready,
secure,
consistent,
recoverable,
idempotent,
scalable,
observable,
and integrated with the existing application architecture."
```

Always prioritize:

```text
Correctness > Convenience
Security > Speed
Server Authority > Client Trust
Data Consistency > Realtime Illusion
Recoverability > Simplicity
Production Safety > Demo Functionality
```
