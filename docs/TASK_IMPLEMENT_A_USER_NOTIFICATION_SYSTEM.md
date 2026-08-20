````md
# TASK: IMPLEMENT A COMPLETE USER NOTIFICATION SYSTEM

## 1. OBJECTIVE

Implement a complete, production-ready **User Notification System** for the existing audio-story website.

The notification system must allow users to receive:

- General website notifications.
- Notifications triggered by administrators.
- VIP-related notifications.
- Payment-related notifications.
- Xu/refund-related notifications.
- Story/chapter updates.
- Chapter deletion notifications.
- Important system announcements.
- Future notification types without requiring a redesign.

The notification system must support both:

1. Persistent in-app notifications.
2. Real-time delivery when the user is online.

The database/persistent notification system is the **source of truth**.

Real-time communication is only the mechanism for immediate delivery.

---

# 2. IMPORTANT IMPLEMENTATION PRINCIPLE

Do NOT immediately start coding.

First:

1. Read this entire Markdown file.
2. Inspect the existing codebase.
3. Understand the existing architecture.
4. Identify existing functionality that can be reused.
5. Identify existing real-time infrastructure.
6. Identify the existing chapter/audio version notification mechanism.
7. Identify existing Xu/wallet/transaction/refund logic.
8. Identify existing VIP logic.
9. Identify existing Navbar and authentication/session architecture.
10. Identify existing event/notification infrastructure.
11. Identify existing database migration conventions.
12. Identify existing testing conventions.

Then create a concise implementation plan internally and execute it.

Do not create duplicate infrastructure when the existing project already provides an appropriate mechanism.

---

# 3. EXISTING ARCHITECTURE MUST BE REUSED

Before implementing anything, search the codebase for:

- WebSocket
- STOMP
- SSE
- Socket.IO
- Redis
- Message broker
- Application events
- Domain events
- Event listeners
- Outbox
- Notification
- Toast
- Chapter version checking
- Audio version checking
- Real-time content update
- Chapter deletion
- Xu refund
- VIP grant

Especially inspect the existing mechanism that detects:

- chapter content changes
- audio changes
- version mismatch
- immediate user notification

If a suitable real-time mechanism already exists, extend it instead of creating a second independent system.

Avoid architectures such as:

```text
WebSocket #1 → Chapter update
WebSocket #2 → Notifications
WebSocket #3 → VIP
````

Prefer a unified event model:

```text
Existing Real-Time Connection
        ↓
Event Type
    ├── CHAPTER_UPDATED
    ├── CHAPTER_DELETED
    ├── VIP_GRANTED
    ├── PAYMENT
    ├── REFUND
    └── SYSTEM
```

Follow the project's existing architecture rather than blindly implementing the example above.

---

# 4. NAVBAR NOTIFICATION ICON

Add a notification icon to the main Navbar.

Position it next to the user's avatar/profile icon.

Example:

```text
[ Other Navbar Items ] [ 🔔 ] [ Avatar ]
```

Requirements:

* Reuse the existing design system.
* Reuse existing icon/button/dropdown components where possible.
* Display unread notification count.
* Open notification dropdown/panel when clicked.
* Show latest notifications.
* Clearly distinguish read and unread notifications.
* Support marking notifications as read.
* Support "Mark all as read" if appropriate.
* Provide access to the complete notification history if necessary.

Do not introduce an unrelated visual style.

---

# 5. NOTIFICATION DROPDOWN

The notification dropdown should display the user's latest notifications.

Each notification may contain:

* Icon.
* Type.
* Title.
* Message.
* Timestamp.
* Read/unread state.
* Related story.
* Related chapter.
* Related transaction.
* Action button/link.

Example:

```text
Notifications

🎉 Congratulations!

You have become a VIP member.

2 minutes ago


📖 Chapter deleted

The chapter you purchased with Xu has been removed.

Your Xu refund has been processed.

[View Refund History]
[Chapter List]

5 minutes ago
```

Unread notifications must be visually distinguishable.

The dropdown must not load the user's entire notification history.

Use pagination or a limited latest-notification query.

---

# 6. PERSISTENT NOTIFICATIONS

Notifications must be persisted in the database.

Real-time delivery must NOT be the source of truth.

Correct behavior:

```text
Business Event
      ↓
Create Notification
      ↓
Persist to Database
      ↓
Transaction Commit
      ↓
Real-Time Delivery
      ↓
User UI
```

If the user is offline, the notification must still exist.

Example:

```text
Admin grants VIP
        ↓
User is offline
        ↓
Notification saved
        ↓
User logs in later
        ↓
Notification is visible
```

---

# 7. NOTIFICATION TYPES

Use an extensible notification type system.

At minimum support:

```text
VIP_GRANTED
CHAPTER_DELETED
CHAPTER_UPDATED
PAYMENT
REFUND
SYSTEM
ANNOUNCEMENT
```

The architecture must allow future types such as:

```text
NEW_CHAPTER
COMMENT_REPLY
FOLLOWED_STORY_UPDATE
ACCOUNT_SECURITY
PROMOTION
MODERATION
```

without requiring a redesign.

Use an enum or equivalent mechanism consistent with the project's architecture.

---

# 8. NOTIFICATION PRIORITY

Where appropriate, support notification priority:

```text
INFO
SUCCESS
WARNING
IMPORTANT
```

Examples:

```text
VIP_GRANTED      → SUCCESS
CHAPTER_DELETED  → IMPORTANT
REFUND           → IMPORTANT
PAYMENT          → IMPORTANT
SYSTEM           → INFO
ANNOUNCEMENT     → INFO
```

Do not make every notification visually intrusive.

---

# 9. VIP GRANTED NOTIFICATION

When an administrator grants VIP to a user, create a notification.

Example:

```text
🎉 Congratulations!

You have become a VIP member.

VIP expires on:
31/12/2026
```

The notification should use the actual VIP information from the backend.

Do not trust VIP information supplied by the frontend.

Flow:

```text
Admin grants VIP
        ↓
VIP business transaction succeeds
        ↓
Create notification
        ↓
Commit
        ↓
Real-time delivery
        ↓
User sees notification
```

If the VIP operation fails or rolls back, do not send a successful VIP notification.

---

# 10. CHAPTER DELETION NOTIFICATION

Integrate this system with the existing paid-chapter deletion functionality.

When an admin deletes a chapter that the user purchased using Xu:

1. Invalidate the user's access immediately.
2. Process the appropriate Xu refund.
3. Create the refund transaction history.
4. Create a persistent notification.
5. After successful commit, deliver the notification in real time.
6. Immediately update the user's reading experience.
7. Prevent continued access to the deleted chapter.

The user should no longer see a generic red error message as the primary UX.

Instead, display a professional notification.

Example:

```text
📖 Chapter deleted

The chapter you purchased with Xu has been removed by the administrator.

Your purchase has been refunded.

Refund:
+100 Xu

[View Refund History]
[Back to Chapter List]
```

The actual refund amount must come from the backend transaction.

Never trust an amount supplied by the frontend.

---

# 11. CHAPTER DELETION BUSINESS TRANSACTION

The preferred logical flow is:

```text
BEGIN TRANSACTION

Delete/invalidate chapter
        ↓
Identify affected users/purchases
        ↓
Calculate refund
        ↓
Refund Xu
        ↓
Create Xu transaction history
        ↓
Create notification/event
        ↓
COMMIT
```

Only after successful commit should the real-time notification be delivered.

If any critical operation fails:

```text
ROLLBACK
```

The system must not leave partial state.

For example, it must never result in:

```text
Chapter deleted
+
Refund failed
+
Notification says refund completed
```

That is invalid.

---

# 12. TRANSACTIONAL CONSISTENCY

For important business operations, notification persistence must be coordinated with the business transaction.

For example:

```text
VIP update
+
Notification
```

or:

```text
Chapter deletion
+
Refund
+
Transaction history
+
Notification
```

must be handled consistently.

If the application already has:

* domain events
* transactional event listeners
* after-commit hooks
* outbox pattern

reuse them.

Do not create a second transaction/event system unnecessarily.

---

# 13. TRANSACTIONAL OUTBOX

Inspect the existing architecture first.

If an appropriate outbox/event persistence mechanism already exists, reuse it.

If reliable event delivery requires it and there is no existing equivalent, implement a lightweight **Transactional Outbox** approach appropriate for the project's scale.

The important principle is:

```text
Business Data
+
Notification/Event
=
same database transaction
```

Then:

```text
COMMIT
   ↓
Outbox/Event Relay
   ↓
WebSocket/SSE
   ↓
User
```

Do NOT rely on:

```text
DB COMMIT
   ↓
send WebSocket
   ↓
hope it succeeds
```

because the database and real-time delivery can fail independently.

Do not introduce Kafka, RabbitMQ, Redis Streams, or other heavy infrastructure unless the existing application genuinely requires it.

For a normal modular monolith, a database-backed outbox or existing after-commit event mechanism is generally preferable.

---

# 14. REAL-TIME DELIVERY

When a user is online, notifications should appear immediately.

Expected flow:

```text
Admin/System Action
        ↓
Business Transaction
        ↓
Successful Commit
        ↓
Notification Event
        ↓
Existing Real-Time Infrastructure
        ↓
User Browser
        ↓
Navbar Badge Updates
        ↓
Notification Appears
```

No page refresh should be required.

Reuse the existing real-time connection where possible.

---

# 15. REAL-TIME IS NOT THE SOURCE OF TRUTH

If:

* WebSocket fails
* SSE fails
* user disconnects
* browser sleeps
* network changes
* server restarts
* user is offline

the notification must not disappear.

The database remains authoritative.

When the user reconnects:

```text
Reconnect
   ↓
Synchronize notification state
   ↓
Fetch missed notifications
   ↓
Update unread count
```

---

# 16. RECONNECTION AND MISSED EVENTS

Handle this case:

```text
User connected
        ↓
Connection lost
        ↓
Admin performs action
        ↓
Notification persisted
        ↓
Real-time delivery unavailable
        ↓
User reconnects
        ↓
Client synchronizes with server
        ↓
Notification appears
```

Do not assume that real-time events are never missed.

---

# 17. AT-LEAST-ONCE DELIVERY

Real-time/event systems should be treated as potentially **at-least-once delivery**.

The system must tolerate duplicate event delivery.

Example:

```text
eventId = ABC123

Received ABC123
Received ABC123 again
```

The second event must not create:

* duplicate notification records
* duplicate UI items
* incorrect unread count
* duplicate refunds
* duplicate business operations

Use a stable:

```text
eventId
notificationId
idempotency key
```

where appropriate.

Do not assume exactly-once delivery.

---

# 18. MULTI-TAB SUPPORT

Consider:

```text
Browser Tab A
Browser Tab B
```

using the same account.

A notification belongs to the user, not to a browser tab.

When Tab A receives a notification:

```text
Server state
    ↓
Notification exists
    ↓
Tab A updates
    ↓
Tab B eventually synchronizes
```

If one tab marks the notification as read, the backend should become the authoritative state.

Other tabs should eventually synchronize.

---

# 19. MULTI-DEVICE SUPPORT

Consider:

```text
Desktop
Mobile
Tablet
```

using the same account.

Notifications should be synchronized through the backend.

If one device marks a notification as read:

```text
Device A → READ
        ↓
Backend
        ↓
Device B synchronizes
```

Do not rely exclusively on local frontend state.

---

# 20. NOTIFICATION ORDERING

For related business events, maintain sensible ordering.

Example:

```text
Chapter deleted
        ↓
Refund processed
        ↓
Notification
```

Do not present a user with an obviously contradictory order.

Do not attempt to guarantee global ordering across unrelated users/events unless the existing architecture requires it.

Use timestamps, event IDs, or business-operation identifiers where appropriate.

---

# 21. NOTIFICATION ACTIONS

Notifications may provide actions.

Examples:

```text
VIEW_REFUND_HISTORY
VIEW_CHAPTER_LIST
VIEW_STORY
VIEW_VIP
VIEW_PAYMENT
```

Prefer storing:

```text
actionType
relatedEntityType
relatedEntityId
metadata
```

rather than hard-coded frontend URLs in backend business logic.

The frontend should resolve actions to routes.

---

# 22. NOTIFICATION DATABASE

First check whether a notification table already exists.

If not, create an appropriate schema following the existing project's conventions.

A possible structure:

```text
notifications
-------------
id
user_id
type
title
message
priority
is_read
created_at
read_at
related_entity_type
related_entity_id
action_type
metadata
event_id
```

This is only a conceptual structure.

Adapt it to the actual project.

Do not blindly copy it.

Sensitive information should not be stored unnecessarily.

---

# 23. DATABASE INDEXES

Design appropriate indexes.

Likely useful indexes include:

```text
user_id
user_id + is_read
user_id + created_at
event_id
```

depending on actual query patterns.

Do not add unnecessary indexes.

Use the project's migration system.

---

# 24. UNREAD COUNT

The Navbar should display unread count.

Example:

```text
🔔 3
```

If there are no unread notifications:

```text
🔔
```

The server should be authoritative.

The frontend may optimistically update state for UX, but it must eventually synchronize with backend state.

---

# 25. READ / UNREAD

Support:

* Mark one notification as read.
* Mark all notifications as read.
* Retrieve unread count.
* Retrieve latest notifications.

Read state must be persisted.

A user should not lose read state after:

* refresh
* logout/login
* reconnect
* changing device

---

# 26. PAGINATION

Do not load the complete notification history into the Navbar.

Use pagination.

Example:

```text
Latest 10 notifications

[Load More]
```

or use a dedicated notification page.

Follow the project's existing pagination conventions.

---

# 27. NOTIFICATION RETENTION

Inspect whether the project already has data-retention policies.

The system should be designed so that old notifications can eventually be cleaned up without breaking active notifications.

Possible future strategies:

* soft delete
* scheduled cleanup
* retention period
* maximum notification count

Do not automatically delete user notifications unless the business requirement explicitly supports it.

---

# 28. ADMIN-SPECIFIC NOTIFICATIONS

The architecture must support notifications generated by administrator actions.

Examples:

```text
Admin grants VIP
Admin removes chapter
Admin updates chapter
Admin suspends account
Admin publishes announcement
```

Do not create a separate notification implementation for every admin feature.

All should use the centralized notification infrastructure.

---

# 29. USER-SPECIFIC NOTIFICATIONS

Support:

```text
notifyUser(userId, notification)
```

or equivalent architecture.

The notification service should centrally handle:

1. Persistence.
2. Event creation.
3. Real-time delivery.
4. Idempotency.
5. Logging where appropriate.

Business services should not manually duplicate notification insertion logic.

---

# 30. SYSTEM-WIDE ANNOUNCEMENTS

The architecture should be capable of supporting:

```text
Admin creates announcement
        ↓
Target:
    - one user
    - selected users
    - all users
```

Do not necessarily implement a full mass-broadcast admin UI unless explicitly required by the current project.

However, the notification architecture should not prevent it later.

---

# 31. SECURITY

Notifications are private user data.

Backend authorization must guarantee:

```text
User A
X
User B notifications
```

Users must only:

* retrieve their own notifications
* mark their own notifications as read
* access their own notification-related data

Never trust:

```text
userId
```

from the frontend when the authenticated user identity is already available from the server-side authentication context.

Use the authenticated principal/session/JWT identity.

---

# 32. ADMIN AUTHORIZATION

Only authorized administrators should be able to trigger administrative notification operations.

Do not trust frontend role checks.

Authorization must be enforced server-side.

---

# 33. XSS / CONTENT SECURITY

Notification content may originate from administrators.

Do not render arbitrary HTML from notification content.

Prevent:

* XSS
* script injection
* malicious HTML
* unsafe URLs

Follow the existing project's sanitization and escaping conventions.

---

# 34. RATE LIMITING AND DUPLICATE CREATION

Protect against accidental notification spam.

Examples:

```text
Admin clicks action twice
Network retry
Frontend retries request
Backend handler executes twice
Event is redelivered
```

Use:

* idempotency keys
* unique event IDs
* database constraints
* transaction checks

where appropriate.

Do not rely only on frontend button disabling.

---

# 35. FAILURE AND RETRY

If notification real-time delivery fails:

```text
Notification remains persisted
```

If an outbox/relay exists, support appropriate retry behavior.

Potential fields:

```text
status
attempt_count
available_at
last_error
processed_at
```

Only implement what is appropriate for the actual architecture.

One failed notification must not permanently block all future notifications.

---

# 36. OBSERVABILITY

Important notification operations should be traceable.

Log appropriate information such as:

```text
notificationId
eventId
userId
notificationType
deliveryAttempt
deliveryResult
```

For failures:

```text
attemptCount
lastError
```

Do not log sensitive personal or financial information unnecessarily.

Follow existing application logging conventions.

---

# 37. SESSION LIFECYCLE

When a user logs in:

```text
Authenticate
    ↓
Load unread count
    ↓
Load latest notifications
    ↓
Connect/reuse real-time connection
    ↓
Subscribe to user-specific events
```

When logging out:

```text
Clear notification state
    ↓
Remove listeners/subscriptions
    ↓
Disconnect/cleanup user-specific channel
```

Prevent User A's notifications from appearing after User B logs into the same browser.

---

# 38. API DESIGN

Follow existing API conventions.

Possible endpoints:

```text
GET    /api/notifications
GET    /api/notifications/unread-count
PATCH  /api/notifications/{id}/read
PATCH  /api/notifications/read-all
```

These are examples only.

Use the actual project's:

* route naming
* API versioning
* response format
* authentication
* error handling
* pagination conventions

---

# 39. FRONTEND ARCHITECTURE

Reuse existing frontend architecture.

Possible components:

```text
NotificationBell
NotificationBadge
NotificationDropdown
NotificationItem
NotificationPage
```

Do not blindly create these exact components if the existing architecture uses a different structure.

---

# 40. MOBILE RESPONSIVENESS

The notification experience must work on:

* Desktop
* Tablet
* Mobile

Use the existing responsive design system.

If a dropdown is unsuitable for mobile, use a drawer/modal/full-page notification view.

---

# 41. CHAPTER VERSION / AUDIO VERSION INTEGRATION

The existing chapter/audio version-change mechanism is important.

Inspect it first.

If it already has:

```text
version mismatch detection
real-time event
user notification
content invalidation
```

integrate it with the new centralized notification system.

Do not maintain two unrelated notification mechanisms.

The final architecture should allow:

```text
CHAPTER_UPDATED
CHAPTER_DELETED
AUDIO_UPDATED
```

to use the same notification infrastructure where appropriate.

---

# 42. EXISTING CHAPTER DELETION UX

The recently implemented behavior where an admin deletes a chapter and the active reader is immediately blocked must remain functional.

After this notification system is implemented:

```text
Admin deletes chapter
        ↓
Active reader immediately loses access
        ↓
Reader receives dedicated professional notification
        ↓
Navbar notification badge updates
        ↓
Notification remains in notification history
```

Do not regress the existing immediate-access-lock behavior.

Do not replace real-time access invalidation with a simple frontend toast.

---

# 43. FINANCIAL SAFETY

The notification system must never alter financial balances independently.

For Xu:

```text
Wallet/ledger system
=
source of truth
```

Notification:

```text
informational representation
```

Never allow a frontend notification action to directly manipulate Xu balance.

Refunds must go through the existing transaction/ledger system.

---

# 44. CONCURRENCY

Consider concurrent operations.

Examples:

```text
Admin deletes chapter
+
User attempts to open chapter
```

or:

```text
Admin grants VIP
+
User refreshes profile
```

or:

```text
Same event processed twice
```

The backend must remain authoritative.

Do not rely on UI state to enforce business rules.

For chapter deletion and refund:

* use the existing transaction strategy
* use appropriate locking/constraints
* prevent duplicate refunds
* prevent duplicate transaction history
* prevent duplicate notifications where necessary

---

# 45. IDEMPOTENCY OF FINANCIAL OPERATIONS

The notification system must never cause financial operations to execute twice.

For example:

```text
CHAPTER_DELETED event
```

must not cause:

```text
Refund #1
Refund #2
```

for the same purchase.

The existing purchase/refund system must remain the authority for determining whether a refund has already occurred.

---

# 46. OFFLINE USER CASE

Explicitly support:

```text
User offline
↓
Admin grants VIP
↓
Notification created
↓
User later logs in
↓
Notification visible
↓
Unread badge correct
```

The same applies to:

* chapter deletion
* refund
* payment
* system announcements

---

# 47. USER DELETED / DISABLED / LOCKED

Inspect the existing account lifecycle.

Consider what should happen if a user is:

* locked
* disabled
* deleted
* anonymized

Notifications must not create security or privacy issues.

Do not allow a locked user to bypass account restrictions through notification links.

Existing account authorization remains authoritative.

---

# 48. DELETED STORY / CHAPTER REFERENCES

If a notification references a chapter/story that has since been deleted:

The notification itself should remain readable.

The UI should gracefully handle missing related entities.

Example:

```text
📖 Chapter deleted

The purchased chapter is no longer available.

[View Refund History]
```

Do not crash because:

```text
chapterId
```

no longer exists.

---

# 49. NOTIFICATION ACTION VALIDATION

Do not assume that a notification action is still valid.

Example:

```text
Notification created:
VIEW_CHAPTER
```

Later:

```text
Chapter deleted
```

When the user clicks:

```text
VIEW_CHAPTER
```

the frontend/backend must gracefully handle the missing resource.

Authorization must also be checked again.

---

# 50. DATA MODEL EXTENSIBILITY

The notification data model should support:

```text
User
Notification Type
Priority
Message
Read State
Related Entity
Action
Metadata
Event ID
Timestamp
```

without requiring schema redesign for every new notification type.

Do not store large arbitrary payloads unnecessarily.

Use metadata carefully.

---

# 51. PERFORMANCE

The implementation must avoid:

* N+1 queries
* full notification table scans
* loading thousands of notifications
* unnecessary WebSocket connections
* excessive unread-count queries
* unbounded in-memory state

Use appropriate indexes and pagination.

Unread count should be efficient.

---

# 52. TESTING

Add tests appropriate to the existing project.

At minimum cover:

## Notification creation

* Creates notification.
* Correct user receives it.
* Notification persists.

## Read/unread

* New notification is unread.
* Mark one as read.
* Mark all as read.
* Correct unread count.

## Authorization

* User cannot access another user's notifications.
* User cannot mark another user's notification as read.

## VIP

* Admin grants VIP.
* VIP succeeds.
* Notification created.
* Real-time event generated.
* Correct message/data.

## Chapter deletion

* Paid chapter deleted.
* Access invalidated.
* Refund processed.
* Transaction history created.
* Notification created.
* Real-time notification delivered.

## Refund failure

* Refund fails.
* Business transaction rolls back.
* No false success notification.

## Duplicate events

* Same event delivered twice.
* No duplicate notification.
* No incorrect unread count.

## Offline

* Notification created while user offline.
* Notification available after reconnect/login.

## Reconnect

* Connection lost.
* Event occurs.
* Connection restored.
* Notification synchronized.

## Multi-tab

* Multiple sessions receive/synchronize notification correctly.

## Multi-device

* Read state remains consistent.

## Pagination

* Large notification history is paginated.

## Security

* Unauthorized access blocked.
* XSS-safe rendering.

---

# 53. INTEGRATION TESTING

Where the project supports integration testing, verify the complete flow:

```text
Admin Action
    ↓
Business Transaction
    ↓
Database State
    ↓
Notification State
    ↓
Real-Time Event
    ↓
Frontend State
```

Especially verify:

```text
Chapter deletion
+
Xu refund
+
Transaction history
+
Notification
```

remain consistent.

---

# 54. MIGRATIONS

If database changes are required:

* create proper migrations
* follow existing migration conventions
* ensure indexes exist
* ensure constraints exist
* verify rollback strategy if supported

Do not manually modify production database schemas outside the project's migration system.

---

# 55. DO NOT OVER-ENGINEER

This is an existing audio-story website.

Do not introduce unnecessary infrastructure.

Do NOT automatically introduce:

* Kafka
* RabbitMQ
* microservices
* separate notification server
* complex distributed event buses

unless the existing architecture already uses them or the actual project requirements justify them.

Prefer the simplest reliable solution compatible with the current project.

For a modular monolith, a centralized notification service + existing real-time mechanism + database persistence/transactional event mechanism is generally preferred.

---

# 56. IMPLEMENTATION WORKFLOW

Follow this workflow:

```text
STEP 1
Read the complete MD
        ↓
STEP 2
Inspect the codebase
        ↓
STEP 3
Understand existing architecture
        ↓
STEP 4
Identify reusable systems
        ↓
STEP 5
Perform business/technical gap analysis
        ↓
STEP 6
Create implementation plan
        ↓
STEP 7
Implement database/backend/frontend
        ↓
STEP 8
Integrate VIP
        ↓
STEP 9
Integrate chapter deletion/refund
        ↓
STEP 10
Integrate existing real-time system
        ↓
STEP 11
Implement persistence/reconnect/idempotency
        ↓
STEP 12
Add tests
        ↓
STEP 13
Run build/tests/lint/type checks
        ↓
STEP 14
Review against this entire MD
        ↓
STEP 15
Fix discovered issues
        ↓
STEP 16
Report final result
```

---

# 57. FINAL ACCEPTANCE CRITERIA

The implementation is complete only when all of the following work.

## Navbar

```text
🔔
```

exists beside the user avatar.

Unread badge works.

Notification dropdown works.

---

## VIP

```text
Admin grants VIP
        ↓
VIP transaction succeeds
        ↓
Notification persisted
        ↓
Real-time notification delivered
        ↓
Navbar badge updates
        ↓
User sees congratulations message
```

---

## Paid Chapter Deletion

```text
Admin deletes paid chapter
        ↓
User access immediately invalidated
        ↓
Refund processed
        ↓
Xu transaction history created
        ↓
Notification persisted
        ↓
Transaction committed
        ↓
Real-time notification delivered
        ↓
Navbar badge updates
        ↓
User sees dedicated deletion notification
        ↓
User can view refund history
        ↓
User can return to chapter list
```

---

## Offline

```text
User offline
        ↓
Admin/system event occurs
        ↓
Notification persisted
        ↓
User reconnects
        ↓
Notification synchronized
        ↓
Unread count correct
```

---

## Duplicate Event

```text
Same event received twice
        ↓
Only one logical notification
        ↓
Unread count remains correct
```

---

## Security

```text
User A
X
User B notification data
```

must always be enforced server-side.

---

## Transaction Safety

No notification may claim:

```text
Refund completed
```

unless the refund actually succeeded.

No financial operation may be duplicated because of notification/event processing.

---

## Real-Time Reliability

Real-time delivery must improve UX without becoming the source of truth.

The persistent notification database remains authoritative.

---

# 58. FINAL IMPLEMENTATION REPORT

After completing the task, report:

## Architecture

Explain the implemented notification architecture.

## Files Changed

List created/modified files.

## Database

List:

* tables
* migrations
* indexes
* constraints

## Backend

List:

* services
* APIs
* events
* transaction changes
* authorization

## Frontend

List:

* Navbar changes
* notification components
* state management
* real-time integration

## Integrations

Explain:

* VIP notifications
* chapter deletion notifications
* Xu refund notifications
* chapter/audio version notifications
* existing real-time system integration

## Reliability

Explain:

* persistence
* transaction boundaries
* after-commit/outbox mechanism
* idempotency
* retry
* reconnect
* multi-tab/device synchronization

## Testing

Report:

* tests added
* tests executed
* results

## Build

Report:

* build
* lint
* type checks
* migration validation

## Remaining Issues

Clearly state any remaining limitations or assumptions.

Do not claim completion if important requirements remain unfinished.

---

# 59. FINAL INSTRUCTION TO THE AGENT

Do not merely describe the solution.

Actually implement it.

The requirements in this file are the functional and architectural specification.

The existing codebase is the technical source of truth.

Use the existing architecture wherever possible.

Do not create duplicate systems.

Do not implement only the frontend.

Do not implement only the backend.

Do not implement only the database.

Implement the entire feature end-to-end:

```text
DATABASE
+
BACKEND
+
BUSINESS LOGIC
+
TRANSACTIONS
+
EVENTS
+
REAL-TIME
+
API
+
FRONTEND
+
NAVBAR
+
UX
+
SECURITY
+
TESTING
```

Before reporting completion:

```text
READ
→ ANALYZE
→ IMPLEMENT
→ TEST
→ REVIEW
→ FIX
→ VERIFY
→ REPORT
```

The task is complete only when the system works correctly under normal, concurrent, offline, reconnect, duplicate-event, failure, and authorization scenarios.

````