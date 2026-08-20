# TASK: IMPLEMENT A COMPLETE GIFT CODE SYSTEM

I want to implement a complete **Gift Code system** for the current audio-story website.

## 1. OBJECTIVE

Admins must be able to create and manage gift codes. When a user enters a valid gift code, the system will add the configured amount of **Xu** to the user's account.

The gift code system must support:

* Configurable Xu reward amount.
* Configurable start time.
* Configurable expiration time.
* Scheduled activation.
* Maximum number of users who can redeem the code.
* Each account can redeem the same gift code only once.
* Enable/disable functionality.
* Usage tracking.
* Redemption history.
* Proper transaction handling.
* Protection against duplicate rewards.
* Protection against race conditions when multiple users redeem the same code simultaneously.

**IMPORTANT:** Read and analyze the existing source code before making changes. Do not create a completely new architecture if the project already has established authentication, authorization, wallet/Xu, transaction, service, repository, database, UI, or admin patterns.

---

# 2. ADMIN SECTION

Add Gift Code management to the Admin area.

Suggested location:

**Admin → Xu Packages → Gift Codes**

If the existing project has a different menu/tab structure, follow the current design system and add an equivalent section.

Admins must be able to:

### 2.1. Create a Gift Code

The creation form should contain at least:

* Gift Code.
* Xu reward amount.
* Start time.
* Expiration time.
* Maximum number of users.
* Active/Inactive status.
* Internal description/note.

Example:

```text
Code: SUMMER2026
Xu: 500
Start: 2026-08-20 00:00
End: 2026-08-31 23:59
Max Uses: 1,000
Status: Active
Description: Summer event gift code
```

---

# 3. SCHEDULED ACTIVATION

A gift code does not have to become active immediately after creation.

The admin must be able to:

* Create a gift code today.
* Set the start time to a future date/time.
* Allow the gift code to exist in the database before it becomes usable.

Example:

```text
Start: 20/08/2026 20:00
End:   31/08/2026 23:59
```

Before 20:00 on August 20:

```text
Gift code is not active yet.
```

After August 31 at 23:59:

```text
Gift code has expired.
```

The system must use a consistent timezone.

Do not hard-code timezone logic in multiple places.

---

# 4. GIFT CODE STATUS

Design a clear gift code status system.

Possible statuses:

```text
SCHEDULED
ACTIVE
EXPIRED
DISABLED
EXHAUSTED
```

Or use an equivalent approach if the current project already has an established status architecture.

Logic:

### SCHEDULED

The gift code exists but its start time has not been reached.

### ACTIVE

The gift code is currently valid and has not been disabled or exhausted.

### EXPIRED

The current time is past the expiration time.

### DISABLED

The admin has manually disabled the gift code.

### EXHAUSTED

The gift code has reached its maximum number of redemptions.

It is not mandatory to persist every status in the database if they can safely be derived from:

* enabled
* startAt
* endAt
* maxUses
* usedCount

Choose the approach that best fits the existing architecture and avoids inconsistent state.

---

# 5. AUTOMATIC CODE GENERATION

In addition to manually entering a gift code, consider supporting:

```text
[ Generate Code ]
```

The system should generate a random gift code.

Examples:

```text
XU2026-8FK29D
SUMMER-7K92LA
VIP-2026-82KD
```

Requirements:

* Must be unique.
* Must have sufficient entropy.
* Do not use the database ID as the gift code.
* Normalize the code when storing and redeeming it.
* Handle uppercase/lowercase consistently.

Recommended behavior:

Gift codes are case-insensitive.

For example:

```text
summer2026
SUMMER2026
Summer2026
```

should all resolve to the same normalized code:

```text
SUMMER2026
```

if this is compatible with the existing system conventions.

---

# 6. MAXIMUM NUMBER OF USERS

Admins must be able to configure:

```text
Maximum Uses
```

Example:

```text
Max Uses = 100
```

Only 100 unique accounts may successfully redeem the gift code.

The 101st user must receive:

```text
Gift code has reached its maximum usage limit.
```

## CRITICAL

Do NOT rely on a simple application-level check such as:

```text
if (usedCount < maxUses) {
    usedCount++;
}
```

without proper concurrency protection.

For example, if 20 requests simultaneously read:

```text
usedCount = 99
maxUses = 100
```

the system must NOT allow all 20 requests to succeed.

Use appropriate mechanisms such as:

* Database unique constraints.
* Transactions.
* Row-level locking / pessimistic locking.
* Atomic updates.
* Or another concurrency-safe mechanism supported by the current database architecture.

The final implementation must guarantee that:

```text
successfulRedemptions <= maxUses
```

at all times.

---

# 7. EACH USER CAN REDEEM A GIFT CODE ONLY ONCE

A user account may redeem a specific gift code **exactly once**.

Example:

User A:

```text
SUMMER2026 → SUCCESS
```

If User A tries again:

```text
SUMMER2026
```

the request must be rejected.

Do not rely only on:

```text
if exists(...)
```

at the application layer.

The database must enforce uniqueness.

Recommended constraint:

```text
UNIQUE(user_id, gift_code_id)
```

or an equivalent database constraint.

This is mandatory for protecting against race conditions.

If the user sends two redemption requests simultaneously, the database must guarantee that only one succeeds.

---

# 8. USER SECTION — REDEEM GIFT CODE

Add a UI where users can enter gift codes.

Suggested location:

```text
Xu Wallet
→ Redeem Gift Code
```

or another location that fits the existing UX.

Example:

```text
Gift Code
[________________]

[ Redeem ]
```

On success:

```text
🎉 Gift code redeemed successfully!
You received 500 Xu.
```

Display the updated Xu balance if the existing UX already supports balance display.

---

# 9. GIFT CODE VALIDATION

When a user redeems a code, the backend must perform all required validations.

At minimum:

### 9.1. Code exists

If not:

```text
Gift code does not exist.
```

### 9.2. Gift code is not disabled

If disabled:

```text
Gift code is currently unavailable.
```

### 9.3. Start time has been reached

If:

```text
now < startAt
```

return:

```text
Gift code is not active yet.
```

### 9.4. Gift code has not expired

If:

```text
now > endAt
```

return:

```text
Gift code has expired.
```

### 9.5. Usage limit has not been reached

If:

```text
usedCount >= maxUses
```

return:

```text
Gift code has reached its maximum usage limit.
```

### 9.6. User has not already redeemed it

If the user has already redeemed it:

```text
You have already redeemed this gift code.
```

### 9.7. Gift code is valid

If all validations pass:

```text
Add Xu
```

---

# 10. TRANSACTION SAFETY — CRITICAL

Gift code redemption must be an **atomic database transaction**.

Recommended flow:

```text
BEGIN TRANSACTION

1. Lock/check gift code
2. Validate gift code
3. Check whether the user has already redeemed it
4. Check usage limit
5. Insert gift code redemption record
6. Add Xu to the user
7. Create Xu transaction/history record
8. Update usage count
9. COMMIT
```

If any step fails:

```text
ROLLBACK
```

The following inconsistent states must NEVER occur:

```text
Redemption record exists
but Xu was not added.
```

or:

```text
Xu was added
but redemption record was not created.
```

or:

```text
usedCount was increased
but the user did not receive Xu.
```

All related operations must succeed or fail together.

---

# 11. INTEGRATE WITH THE EXISTING XU SYSTEM

Before implementing anything, inspect the existing code for:

* User balance.
* Xu balance.
* Wallet.
* Coin/currency.
* Transaction.
* Payment.
* Top-up.
* Purchase.
* Chapter unlocking.
* VIP.
* Transaction history.

If the project already has a service responsible for adding/removing Xu, **DO NOT create another balance system**.

Reuse the existing architecture.

For example, if the project already has:

```text
WalletService
CoinService
TransactionService
```

the Gift Code system must integrate with those services.

Do NOT directly execute something like:

```sql
UPDATE users SET xu = xu + 500
```

if the existing architecture already provides a Wallet/Coin service for managing balances.

---

# 12. XU TRANSACTION HISTORY

Every successful gift code redemption must create a transaction/history record.

Example:

```text
Type: GIFT_CODE
Amount: +500 Xu
Description: Redeemed gift code SUMMER2026
Reference: SUMMER2026
```

If the current system already has transaction history, integrate with it.

Do not create a separate transaction-history mechanism if one already exists.

---

# 13. DATABASE DESIGN

First analyze the existing database.

If appropriate tables do not exist, create something similar to:

```text
gift_codes
-----------
id
code
amount
start_at
end_at
max_uses
used_count
status / enabled
description
created_by
created_at
updated_at
```

And:

```text
gift_code_redemptions
---------------------
id
gift_code_id
user_id
redeemed_at
xu_amount
created_at
updated_at
```

Mandatory constraints:

```text
UNIQUE(gift_code_id, user_id)
```

and:

```text
UNIQUE(code)
```

If the code is normalized, the uniqueness constraint must apply to the normalized value.

Use foreign keys if the existing architecture uses them.

---

# 14. ADMIN AUDIT LOG

If the existing system has audit logging, record important admin actions:

* Create gift code.
* Update gift code.
* Enable gift code.
* Disable gift code.
* Generate gift code.
* Delete gift code, if deletion is supported.

Audit information may include:

```text
Admin
Action
Gift Code
Timestamp
Old Value
New Value
```

Follow the existing audit-log architecture rather than creating a separate system.

---

# 15. ADMIN GIFT CODE LIST

Create an admin table for managing gift codes.

Suggested columns:

```text
Code
Xu
Start Time
End Time
Used / Max Uses
Status
Created At
Actions
```

Example:

```text
SUMMER2026
500 Xu
20/08/2026 00:00
31/08/2026 23:59
124 / 1000
ACTIVE
```

Admin should be able to:

* Search by code.
* Filter by status.
* Filter by date.
* Sort.
* Paginate.

---

# 16. VIEW GIFT CODE REDEMPTIONS

When an admin opens a gift code:

Display:

```text
Gift Code: SUMMER2026
Xu: 500
Used: 124 / 1000
Start: ...
End: ...
Status: ACTIVE
```

Also display the list of users who redeemed it:

```text
User
Username
Redeemed At
Xu Received
```

Use pagination for large datasets.

---

# 17. EDITING GIFT CODES

Admins may edit appropriate fields.

Be especially careful with:

* amount
* startAt
* endAt
* maxUses
* code

Example:

If:

```text
usedCount = 500
```

the admin must NOT be allowed to change:

```text
maxUses = 100
```

because this creates an invalid state.

Backend validation is mandatory. Do not rely only on frontend validation.

If a gift code has already been redeemed, consider preventing changes to the `code` itself to preserve audit/history consistency.

---

# 18. DELETING GIFT CODES

Do not hard-delete gift codes that have already been redeemed.

If redemption records exist:

```text
DO NOT physically delete
```

Instead:

```text
DISABLED
```

or use soft deletion.

The goal is to preserve redemption history and auditability.

---

# 19. SECURITY

Never trust frontend-provided financial values.

The frontend should only send something like:

```json
{
  "code": "SUMMER2026"
}
```

The backend must determine the authenticated user from the authentication/session/token context:

```text
authenticatedUser.id
```

Do NOT allow the frontend to decide:

```text
userId
xuAmount
```

The Xu amount must always come from the gift code stored in the database.

For example, the backend must NOT trust:

```json
{
  "code": "SUMMER2026",
  "userId": 123,
  "amount": 999999
}
```

---

# 20. ERROR HANDLING

Do not expose raw database exceptions or internal server errors to users.

Map business errors to clear error codes/messages.

Possible error codes:

```text
INVALID_GIFT_CODE
GIFT_CODE_NOT_STARTED
GIFT_CODE_EXPIRED
GIFT_CODE_DISABLED
GIFT_CODE_EXHAUSTED
GIFT_CODE_ALREADY_REDEEMED
GIFT_CODE_INVALID
```

If the existing project already has an error-code convention, follow it.

---

# 21. ADMIN AUTHORIZATION

Only admins with the appropriate permissions may:

* Create gift codes.
* Edit gift codes.
* Enable/disable gift codes.
* View redemption lists.
* View gift code statistics.
* Delete/soft-delete gift codes.

Normal users may only:

```text
Redeem Gift Code
```

Authorization must be enforced on the backend.

Do not simply hide admin buttons in the frontend.

---

# 22. USER EXPERIENCE

Use the existing component library and design system.

Do not introduce a completely different visual style.

When redeeming:

### Loading

```text
Checking gift code...
```

Prevent users from repeatedly submitting the request while it is processing.

### Success

```text
🎉 Gift code redeemed successfully!
You received +500 Xu.
```

### Error

Display the appropriate business error.

Do not unnecessarily clear the input field when redemption fails.

---

# 23. ADMIN VALIDATION

Backend validation must include:

```text
code != empty
amount > 0
startAt < endAt
maxUses > 0
code is unique
```

If unlimited usage is supported, design it explicitly.

For example:

```text
NULL = unlimited
```

or:

```text
Unlimited
```

Do not use magic values such as:

```text
maxUses = -1
```

unless the existing project already follows that convention.

---

# 24. TIMEZONE

This is extremely important.

Inspect the current project and determine:

* Database timezone.
* Backend timezone.
* JVM timezone, if applicable.
* Frontend timezone.
* Whether the system already uses UTC.
* Whether the system already uses `Asia/Ho_Chi_Minh`.

Then standardize the implementation.

Recommended architecture:

```text
Database: UTC
Backend: UTC
Frontend: Convert/display in the required timezone
```

However, follow the project's existing convention if it already has one.

Avoid situations such as:

```text
Admin sees:
20:00

Backend interprets:
13:00
```

---

# 25. API DESIGN

Design APIs according to the project's existing REST/API conventions.

Possible structure:

```text
POST   /api/gift-codes/redeem

GET    /api/admin/gift-codes
POST   /api/admin/gift-codes
GET    /api/admin/gift-codes/{id}
PUT    /api/admin/gift-codes/{id}
PATCH  /api/admin/gift-codes/{id}/status
GET    /api/admin/gift-codes/{id}/redemptions
```

These endpoints are examples only.

Follow the existing project's naming, versioning, authentication, response, and error conventions.

---

# 26. IDEMPOTENCY AND RACE CONDITIONS

You must specifically test the following cases.

### Case 1 — User double-clicks Redeem

The user submits two requests almost simultaneously.

Expected:

```text
1 SUCCESS
1 ALREADY_REDEEMED
```

The user must NOT receive the reward twice.

### Case 2 — Two browser tabs

The same user redeems the same code from two tabs simultaneously.

Expected:

```text
1 SUCCESS
1 ALREADY_REDEEMED
```

### Case 3 — Maximum usage

A gift code has:

```text
maxUses = 100
```

and many users redeem it simultaneously.

Expected:

```text
Exactly 100 successful redemptions
```

Never:

```text
101+
```

### Case 4 — Server failure during transaction

If the server crashes or the transaction fails:

```text
Database transaction must roll back.
```

There must be no partial Xu balance update.

---

# 27. TESTING

Write tests for the complete business logic.

At minimum:

## Creation

* Valid gift code.
* Duplicate code.
* Invalid Xu amount.
* Invalid date range.
* Invalid maximum usage.

## Redemption

* Valid redemption.
* Invalid code.
* Code not started.
* Code expired.
* Code disabled.
* Code exhausted.
* User already redeemed.
* Unauthenticated user.

## Xu

* Correct Xu amount added.
* Xu transaction history created.
* No Xu added when the transaction rolls back.

## Concurrency

Test:

```text
Same user + same gift code concurrently
```

and:

```text
Many users + limited gift code concurrently
```

Concurrency testing is mandatory.

---

# 28. DO NOT BREAK THE EXISTING XU SYSTEM

Before implementing, locate:

1. User entity/model.
2. Wallet/Xu balance.
3. Xu/Coin service.
4. Transaction/ledger system.
5. Authentication.
6. Authorization/admin roles.
7. Admin menu/navigation.
8. Xu package management UI.
9. Database migration system.
10. Error handling conventions.
11. Testing conventions.

Then implement Gift Codes using the existing architecture.

Do not:

* Duplicate the balance system.
* Create a second transaction system.
* Duplicate wallet logic.
* Bypass existing services.
* Introduce unnecessary architectural changes.

---

# 29. DATABASE MIGRATION

If database changes are required:

* Use the project's existing migration framework.
* Do not manually modify the production database.
* Migration must be reproducible.
* Add required indexes.
* Add unique constraints.
* Add foreign keys if consistent with the current architecture.

Make sure the migration works against the existing database schema.

---

# 30. PERFORMANCE

Gift codes may be released during events and could receive many simultaneous redemption requests.

Therefore:

* `gift_codes.code` must have an index/unique index.
* `(gift_code_id, user_id)` must have a unique index.
* Do not scan all redemption records to calculate usage on every redemption if `used_count` is maintained.
* `used_count` must be updated transactionally.
* Redemption history must be paginated.
* Do not load all redemption records into the frontend.

---

# 31. ADMIN STATISTICS

If the current admin dashboard supports statistics, consider adding:

```text
Total Gift Codes
Active Gift Codes
Total Redemptions
Total Xu Distributed
```

For an individual gift code:

```text
Total Uses
Remaining Uses
Total Xu Distributed
```

Example:

```text
SUMMER2026

500 Xu / user

Used:
124 / 1000

Remaining:
876

Total Xu Distributed:
62,000 Xu
```

All statistics must be derived from reliable database data.

Never trust frontend-provided values.

---

# 32. DELIVERABLES

After implementation, provide:

1. List of changed files.
2. Database migration(s).
3. New API endpoints.
4. New entities/models.
5. Service/business logic.
6. Admin UI.
7. User redemption UI.
8. Authorization changes.
9. Tests.
10. Any required configuration changes.

Also provide a brief explanation of the final Gift Code architecture.

---

# 33. ACCEPTANCE CRITERIA

The feature is considered complete only when the following workflow works correctly:

```text
Admin creates gift code
        ↓
Sets Xu reward
        ↓
Sets Start Time
        ↓
Sets End Time
        ↓
Sets Maximum Uses
        ↓
Activates / schedules code
        ↓
User enters code
        ↓
Backend validates code
        ↓
Checks time
        ↓
Checks status
        ↓
Checks usage limit
        ↓
Checks whether user already redeemed it
        ↓
Starts transaction
        ↓
Adds Xu
        ↓
Creates Xu transaction history
        ↓
Creates redemption record
        ↓
Updates usage count
        ↓
Commits transaction
```

If any step fails:

```text
ROLLBACK
```

There must be no inconsistent database state.

---

# 34. IMPORTANT — DEVELOPMENT PROCESS

Do NOT immediately start writing code.

Follow this sequence:

## STEP 1 — ANALYZE

Read the existing source code and identify:

* Backend framework.
* Frontend framework.
* Database.
* Authentication.
* Authorization.
* Xu/Wallet architecture.
* Transaction architecture.
* Admin architecture.
* Migration system.
* Existing UI patterns.
* Existing testing patterns.

## STEP 2 — PLAN

Create a concrete implementation plan:

```text
Database
→ Backend
→ Transaction logic
→ API
→ Admin UI
→ User UI
→ Tests
```

Clearly identify the files/modules that will likely need to be changed.

## STEP 3 — IMPLEMENT

Implement the feature according to the plan.

Reuse existing code wherever possible.

Do not duplicate existing business logic.

## STEP 4 — TEST

Run:

* Unit tests.
* Integration tests.
* Build.
* Lint/type checks, if available.
* Database migration validation.

## STEP 5 — REVIEW

Perform a final review specifically for:

* Double redemption.
* Race conditions.
* Maximum usage enforcement.
* Transaction rollback.
* Authorization.
* Xu balance consistency.
* Timezone correctness.
* Duplicate gift codes.
* Audit/history consistency.

If any issue is found, fix it before reporting the task as complete.

---

# FINAL REQUIREMENT

This is NOT simply a CRUD feature for managing codes.

Treat gift code redemption as a **financial-like currency operation**, because it directly creates Xu in a user's account.

The highest priorities are:

```text
DATA CONSISTENCY
TRANSACTION SAFETY
CONCURRENCY SAFETY
NO DUPLICATE REWARDS
AUDITABILITY
SECURITY
```

Do not accept an implementation that only validates conditions on the frontend or relies solely on application-level checks without database constraints and transaction/concurrency protection.

Before considering the task complete, prove through tests or a clear technical explanation that:

```text
1. A user cannot redeem the same gift code twice.
AND
2. A gift code cannot exceed its configured maxUses.
AND
3. Xu cannot be added if the redemption transaction fails.
AND
4. A successful redemption always keeps the following consistent:

   User Xu Balance
   +
   Xu Transaction History
   +
   Gift Code Redemption Record
   +
   Gift Code Usage Count
```

All four pieces must remain consistent even under concurrent redemption requests or transaction failures.
