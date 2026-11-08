# Planning Service — Business Logic

This document collects the business rules, calculation logic, and cross-service behavior of the
Planning Service that used to live as inline code comments. It is the source of truth for *why*
the code behaves the way it does; the source files linked below (by class and method name — no
line numbers, since those drift) show *how*.

## Savings Goal Calculation

- Creating a plan generates its full milestone schedule up front, at creation time: one milestone
  per interval (day/month/year, depending on the plan's frequency)
  (see `PlanEntity`, `MilestoneCalculator.generateSchedule`).
- The target amount is split evenly across milestones using floor division to 2 decimal places,
  with any remainder allocated entirely to the last milestone, so the milestone amounts always sum
  back to exactly the target amount (see `MilestoneCalculator.generateSchedule`).
- The number of milestones is computed differently per frequency: `DAILY` counts the calendar days
  between the start date and start date + duration; `MONTHLY` uses the effective month count
  directly; `ANNUALLY` uses `durationInYears` when supplied, otherwise ceiling-divides the
  effective months by 12 (so a 25-month duration produces 3 annual milestones, not 2 via
  truncation) (see `MilestoneCalculator.resolvePeriodCount`).
- A schedule is capped at 3650 generated milestones (e.g. to prevent a multi-decade `DAILY` plan)
  and must produce at least one milestone — both are rejected with `INVALID_REQUEST`
  (see `MilestoneCalculator.generateSchedule`).
- `MilestoneCalculator.generateSchedule` returns milestone entities that are not yet attached to a
  plan or persisted; the caller (`PlanServiceImpl.createPlan`) is responsible for setting the
  `plan` association on each one before saving — this ordering matters for correctness.

## Plan Creation & Field Normalization (Frontend Integration)

- `CreatePlanRequest` accepts both the internal field names and the frontend's aliases via
  `@JsonAlias`: `title` → `goalTitle`, `savingsPerPeriod` → `requiredPerPeriod`
  (see `CreatePlanRequest`).
- The frontend sends a flat `duration` (in months); when `durationInMonths` is not separately
  supplied, it is copied from `duration`. When `timeframeCategory` is omitted, it is derived from
  the duration fields (see `PlanServiceImpl.normalizeRequest`).
- `startDate` defaults to today when the request omits it (see `PlanServiceImpl.normalizeRequest`).
- `Frequency` values are accepted case-insensitively (e.g. `"monthly"` or `"MONTHLY"`) via
  `spring.jackson.mapper.accept-case-insensitive-enums: true` (see `CreatePlanRequest`).
- `PlanCategory` has eight values matching the options presented in the frontend's plan-creation
  form, serialized as uppercase strings (see `PlanCategory`).

## Timeline & Deadline Rules

`TimeframeCategory` determines both the valid duration range for a plan and which `Frequency`
values are permitted (see `TimeframeCategory`, `Frequency`, `MilestoneCalculator.validateCombination`):

| Timeframe category | Duration range | Allowed frequency | Required duration field |
|---|---|---|---|
| `SHORT_TERM` | 3–11 months | `DAILY` or `MONTHLY` | `durationInMonths` |
| `MID_TERM` | 12–60 months (1–5 years) | `DAILY` or `MONTHLY` | `durationInMonths` |
| `LONG_TERM` | 61–240 months (5–20 years) | `MONTHLY` or `ANNUALLY` | `durationInMonths` or `durationInYears` |

- The duration range is enforced whenever `durationInMonths` is present, even if the client sends
  `timeframeCategory` explicitly (e.g. `SHORT_TERM` + 200 months is rejected). `duration` and
  `durationInMonths` are capped at 240 (see `MilestoneCalculator.requireMonthsInRange`).

- `durationInMonths` is the field used for `SHORT_TERM`/`MID_TERM` plans. `durationInYears` is a
  legacy field retained for `LONG_TERM` plans created before the flat-duration (`durationInMonths`)
  migration (see `PlanEntity`).
- Each milestone's date window is anchored on the plan's `startDate`, so the first milestone is
  always a full period. A plan starting mid-month does not get only the tail of the calendar month
  (see `MilestoneCalculator.resolveIntervalDates`):
  - `DAILY` → `"Day N"`, a single day (`startDate + N-1 days`).
  - `MONTHLY` → milestone *i* runs from `startDate + i months` to the day before
    `startDate + (i+1) months`. Every bound is computed from `startDate` itself, so month-length
    clamping (Jan 31 → Feb 28/29) never accumulates into gaps or overlaps. Label: `"November 2026"`
    when the plan starts on the 1st (the window is then exactly that calendar month), otherwise
    `"Month N (25 Nov 2026 – 24 Dec 2026)"`.
  - `ANNUALLY` → milestone *i* runs from `startDate + i years` to the day before
    `startDate + (i+1) years`. Label: `"Year N (2027)"` when the plan starts on Jan 1, otherwise
    `"Year N (Jun 2027 – Jun 2028)"`.
  - Schedules persisted before this rule keep their calendar-aligned windows; only new plans use it.

## Progress Tracking & Live Status

- A milestone's `status` and its effective `targetSavings` are always recomputed live, at read
  time, against the current date — the persisted `status`/`targetSavings` columns are a
  best-effort cache only and must never be treated as the source of truth
  (see `MilestoneEntity`, `MilestoneResponse`, `MilestoneCalculator.deriveStatus`,
  `MilestoneCalculator.computeLiveMilestones`).
- Status precedence, highest wins: **COMPLETED** (`actualSaved >= targetSavings`, OR the milestone
  was explicitly marked complete) beats **OVERDUE** (deadline has passed and it isn't complete)
  beats **PENDING** (otherwise) (see `MilestoneStatus`, `MilestoneCalculator.deriveStatus`).
- Marking a milestone complete (`POST /milestones/{id}/complete`) sets `actualSaved` to the
  milestone's current effective target savings, unless it is already higher, and sets the
  manual-completion flag. When it tops the amount up, the previous amount is remembered in
  `actualSavedBeforeCompletion`. Completing an already-completed milestone is a no-op
  (see `MilestoneEntity.manuallyCompleted`, `PlanServiceImpl.completeMilestone`).
- Undoing completion (`POST /milestones/{id}/undo`) clears the flag and restores the remembered
  amount, so the status really reverts to `PENDING`/`OVERDUE`. An explicit `PATCH` of `actualSaved`
  discards the remembered amount: the user's own number wins over an undo. Milestones completed
  before this column existed have nothing to restore, so their undo only clears the flag.
- "Today" is evaluated in `Asia/Ho_Chi_Minh` via the shared `Clock` bean, not the server zone
  (Render runs in UTC) — this drives OVERDUE, future-dated expense checks and the default plan
  start date (see `ClockConfig`).
- Plan-level totals are always derived, never stored: `totalSaved` = sum of `actualSaved` across
  ALL milestones, whatever their status — partial savings count, because progress must reflect
  real money; `remaining` = `max(0, targetAmount - totalSaved)`;
  `progressPercent` = `min(100, totalSaved / targetAmount * 100)`, rounded to 2 decimal places
  (see `MilestoneCalculator.computeTotals`, `PlanResponse`, `PlanSummaryResponse`).

## Deficit Redistribution

- Every plan has a `recalculateOnMissedDeadline` toggle, off by default. When enabled, the open
  milestones' targets are recomputed every time the plan is read so the plan still lands exactly
  on its target amount. When disabled, the originally generated schedule is left untouched
  (see `PlanEntity.recalculateOnMissedDeadline`, `MilestoneCalculator.computeLiveMilestones`).
- Redistribution algorithm: a milestone is *open* when, judged against its base target, it is
  `PENDING` (deadline not passed, not completed); every other milestone is *closed*. The open
  milestones owe `max(0, Σ baseTargetSavings − Σ actualSaved of closed milestones)` — base targets
  always sum to the plan's target amount — split evenly, floor-divided to 2 decimal places, with
  the leftover cent on the last open milestone. If no milestone is open, nothing changes.
- Invariant: the amount the user is asked to save in total never exceeds the target. Deficits on
  overdue milestones raise the open targets; over-saving on a closed milestone lowers them (down
  to 0, at which point the open milestones count as completed). Completing an open milestone at
  its raised target moves it to *closed* without changing what the others owe.
- A manually completed milestone saved below its base target still leaves that shortfall to the
  open milestones: the flag closes the period, it does not waive the money.

## Expense Tracking

- An expense's `expenseType` (fixed/variable) is copied from its category's `defaultType` at the
  moment the expense is created, and is never re-joined live afterwards. Editing a category's
  `defaultType` later does **not** retroactively change any already-recorded expense rows. The
  only way to change an existing expense's type is an explicit `PATCH` update
  (see `CreateExpenseRequest`, `Expense.expenseType`, `ExpenseCategory.defaultType`,
  `ExpenseServiceImpl.createExpense`, `UpdateExpenseRequest`).
- `spentOn` may never be a future date. This check is enforced in the service layer rather than as
  a Bean Validation annotation because it needs "today" at request-handling time, not a fixed
  validation-time constant (see `ExpenseServiceImpl.rejectFutureDate`).
- Expense list queries return all expenses (all time) when no date range is supplied. A caller must
  supply both `from` and `to` or neither — supplying only one is rejected, as is `to` before `from`.
  There is no maximum span (see `DateRange.resolve`, `ExpenseController`,
  `ExpenseServiceImpl.listExpenses`).
- An expense can optionally be linked to one of the caller's savings plans (`planId`, nullable column
  `expenses.plan_id`). On create, bulk create and update, the plan must exist, not be soft-deleted
  and belong to the caller. Otherwise the request fails with `EXPENSE_PLAN_MISMATCH` (bulk: a per-row
  message, nothing saved). The error is the same whether the plan is missing or someone else's, so
  plan IDs cannot be probed (see `ExpenseServiceImpl.verifyPlanOwnership`,
  `ExpenseServiceImpl.findOwnedPlanIdsIn`).
- On `PATCH`, `planId` omitted/null leaves the link unchanged and an empty string unlinks it.
- The list **and** the summary both accept an optional `planId` filter through the shared
  `SEARCH_FILTER`, so they still cannot disagree; the summary echoes it back.
- Deleting a plan clears `plan_id` on every expense linked to it before the plan is removed, so no
  expense points at a plan that no longer exists. The expense itself is kept, since it is still real
  spending (see `ExpenseRepository.unlinkPlan`, `PlanServiceImpl.deletePlan`).
- `currency` is restricted to the `Currency` enum (`VND`/`USD`, case-insensitive on input) and
  stored uppercase, so the per-currency summary never splits `usd` from `USD`.
- `GET /expenses` pages are clamped to `size` 1–100 (`ExpenseServiceImpl.MAX_PAGE_SIZE`).
- **Delete is a hard delete** for both expenses and plans: the row is physically removed (a plan
  together with its milestones). Rows soft-deleted before this change still exist with
  `is_deleted = true`, so every read keeps filtering `deleted = false`
  (see `ExpenseServiceImpl.deleteExpense`, `PlanServiceImpl.deletePlan`).

## Expense Categories

- The first time a user requests their expense categories, six system-default categories are
  seeded and returned: **Rent**, **Utilities**, **Subscriptions** (all `FIXED`), and **Groceries**,
  **Transport**, **Dining out** (all `VARIABLE`) — insertion order is preserved in the response
  (see `ExpenseCategoryServiceImpl.listCategories`, `ExpenseCategorySeeder.SYSTEM_DEFAULTS`).
- Seeding runs in its own `REQUIRES_NEW` transaction (`ExpenseCategorySeeder.seedDefaults`). When two
  first-time requests race (e.g. the frontend loading two widgets at once), the loser hits
  `uk_expense_categories_user_name`. Only its own seeding rolls back, and it returns the categories
  the winner inserted instead of a 500.
- The `system` flag (serialized as `isSystem`) distinguishes auto-seeded defaults (`true`) from
  user-created categories (`false`), so the frontend can tell the two apart
  (see `ExpenseCategory.system`, `ExpenseCategoryResponse.system`).
- Creating a category rejects a name the user already uses, case-insensitively (e.g. "Groceries"
  collides with "groceries"), returning a 409 `DUPLICATE_CATEGORY_NAME`. A same-name category created
  concurrently, which slips past that pre-check, is caught at insert and gets the same 409
  (see `ExpenseCategoryServiceImpl.createCategory`,
  `ExpenseCategoryRepository.existsByUserIdAndNameIgnoreCaseAndDeletedFalse`).

## Data Ownership & Multi-Tenancy

- `userId` on `Expense`, `ExpenseCategory`, and `PlanEntity` is a soft link — a plain `VARCHAR`
  UUID referencing the user record that actually lives in `auth-service`. There is no physical
  foreign key across services (the two services own separate databases), so every query must
  filter by `userId` to enforce multi-tenancy (see `Expense`, `ExpenseCategory`, `PlanEntity`,
  `PlanRepository.findByIdAndUserId`).
- By contrast, `MilestoneEntity.plan` is a real, physical foreign key, since milestones and their
  parent plan live in the same service/database (see `MilestoneEntity.plan`).
- `MilestoneRepository.findByIdAndPlanId` scopes milestone lookups to a specific plan, so a
  milestone ID cannot be used to reach into a plan it doesn't belong to.
- Ownership is enforced in the lookup query itself (`findByIdAndUserIdAndDeletedFalse`): a plan or
  expense that is missing, legacy soft-deleted, or owned by someone else returns the exact same
  `404`, so a caller cannot distinguish "doesn't exist" from "exists but isn't yours"
  (see `PlanServiceImpl.getOwnedPlanOrThrow`, `ExpenseServiceImpl.getOwnedExpenseOrThrow`).

## Cross-Service Interactions

- Planning Service never issues JWTs — it only validates tokens issued at login by
  `auth-service`, verifying the signature and expiry and extracting the `userId` claim (falling
  back to the JWT `subject` when no explicit `userId` claim is present), so requests can be scoped
  to their owner (see `JwtService`, `JwtAuthFilter`).
- The JWT signing secret is shared with `auth-service` via the `FINTRACK_JWT_SECRET` environment
  variable convention used across the whole platform (`fintrack.jwt.secret` in
  `application.yml`); a missing secret, or one shorter than 32 characters or containing the
  placeholder `"change-me"`, causes the service to refuse to start (see `JwtService`).
- `JwtAuthFilter` only attaches an authenticated principal to the security context when a valid
  token is present; requests with no token, or an invalid/expired one, are allowed to continue
  unauthenticated through the filter and are rejected downstream by `SecurityConfig`'s
  path-authorization rules with a `401` (see `JwtAuthFilter`, `SecurityConfig`).
- `JwtAuthFilter` is registered explicitly inside the `SecurityFilterChain` via
  `addFilterBefore`, and `SecurityConfig` deliberately disables Spring Boot's automatic
  `FilterRegistrationBean` registration for it — otherwise the filter would run twice per request
  (once in Spring Security's chain, once at the servlet-container level)
  (see `SecurityConfig.jwtFilterRegistration`).
- Every controller endpoint, except `/ping` and the actuator/Swagger paths, requires a JWT issued
  by `auth-service`. The authenticated `userId` is injected via `@AuthenticationPrincipal` and used
  to scope and own every operation (see `PlanController`, `ExpenseController`,
  `ExpenseCategoryController`, `SecurityConfig`).
- Known gap: an expense's `currency` is not validated against the user's account currency, because
  that preference is owned by `auth-service` and there is currently no client available to fetch
  it cross-service (see `ExpenseServiceImpl.createExpense`).
- The `Currency` enum (`VND`, `USD`) is intentionally duplicated locally rather than shared with
  `auth-service`, to avoid introducing a compile-time coupling between the two services
  (see `Currency`).
- `/ping` is a public, dependency-free liveness endpoint (touches no database, repository, or
  service-layer bean) that the frontend uses to pre-warm this service: Render's free tier spins the
  service down after roughly 15 minutes idle and takes several minutes to cold-start, so the
  frontend calls `GET /ping` as soon as the user reaches the login page, ahead of the dashboard's
  calls to `/api/v1/plans` (see `WarmupController`).
- The AI budget-planning endpoint (`POST /api/v1/plans/ai/generate`) sends the user's free-text
  prompt to Google Gemini via Spring AI's `ChatClient`. When the user doesn't specify explicit
  allocation percentages, the model is instructed to apply the standard 50/30/20 budgeting rule
  (50% necessities, 30% wants, 20% savings/investments) and must return the budget breakdown as
  strict JSON (see `GeminiConfig`, `PlanServiceImpl.generateAiPlan`). Prompts are capped at 2000
  characters, and the response's money and percentage fields are `BigDecimal`.

## Dashboard Summary (`GET /api/v1/plans/summary`)

- Returns one entry per currency (`planCount`, `completedPlanCount`, `totalTarget`, `totalSaved`,
  `remaining`); currencies are never combined. It aggregates over ALL of the caller's plans, so it is
  independent of any list paging. Money fields are strings to avoid floating-point loss in JS clients.
  The caller is always taken from the JWT (see `PlanController.getSummary`).
- `totalSaved` follows the per-plan rule in `MilestoneCalculator.computeTotals`: the sum of
  `actual_saved` over all of the plan's milestones, partial savings included. It only reads stored
  columns, so it is exact with or without deficit redistribution (see `PlanRepository.summarizeByUser`).
  `completedPlanCount` counts plans whose saved total has reached their target.
- `remaining` is clamped to 0 per plan before summing, matching each plan card, so an over-saved plan
  never offsets another plan's shortfall.
- Consistency: the summary is a single read-only query with no cross-request locking. A summary loaded
  while a milestone is being saved may reflect either side of that write; the next load is correct.

## Expense Summary (`GET /api/v1/expenses/summary`)

- Returns the applied filter (`from`, `to`, `type`, `categoryId`; each is null when unset) and `totals`: spent (`total`), `fixed` and `variable` plus a
  `count`, one entry per currency (never combined), over ALL expenses matching the window/`type`/`categoryId` filters — independent of
  the list endpoint's paging. `fixed + variable == total`. Money fields are strings.
- It shares the list query's WHERE clause (`ExpenseRepository.SEARCH_FILTER`) and the same
  `DateRange.resolve` rules (supply both dates or neither; neither means all time; there is no maximum span), so the totals
  and the list cannot disagree about which expenses match. With a `type` filter applied, the other
  type's total is 0.
- The list accepts a `planId` parameter that its query ignores; the summary deliberately has none.
