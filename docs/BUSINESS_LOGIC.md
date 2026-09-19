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

- `durationInMonths` is the field used for `SHORT_TERM`/`MID_TERM` plans. `durationInYears` is a
  legacy field retained for `LONG_TERM` plans created before the flat-duration (`durationInMonths`)
  migration (see `PlanEntity`).
- Each milestone's timeline label and date window are derived from its position in the schedule and
  the plan's frequency: `DAILY` → `"Day N"` labeling a single calendar day; `MONTHLY` →
  `"Month YYYY"` labeling a calendar month (deadline = last day of that month); `ANNUALLY` →
  `"Year N (YYYY)"` labeling a calendar year (deadline = December 31st)
  (see `MilestoneCalculator.resolveIntervalDates`).

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
  milestone's current effective target savings, unless it is already higher — and sets the
  manual-completion flag regardless of whether the amount saved actually reached the target
  (see `MilestoneEntity.manuallyCompleted`, `PlanServiceImpl.completeMilestone`).
- Undoing completion (`POST /milestones/{id}/undo`) only clears the manual-completion flag; the
  displayed status then reverts to whatever the live computation derives (`PENDING` or `OVERDUE`)
  based on the amount saved and the current date.
- Plan-level totals are always derived, never stored: `totalSaved` = sum of `actualSaved` across
  milestones whose live status is `COMPLETED`; `remaining` = `max(0, targetAmount - totalSaved)`;
  `progressPercent` = `min(100, totalSaved / targetAmount * 100)`, rounded to 2 decimal places
  (see `MilestoneCalculator.computeTotals`, `PlanResponse`, `PlanSummaryResponse`).

## Deficit Redistribution

- Every plan has a `recalculateOnMissedDeadline` toggle, off by default. When enabled, deficits
  from overdue milestones are redistributed evenly across future, not-yet-completed milestones
  every time the plan is read. When disabled, the originally generated schedule is left untouched
  (see `PlanEntity.recalculateOnMissedDeadline`, `MilestoneCalculator.computeLiveMilestones`).
- Redistribution algorithm: every milestone whose deadline has passed without being completed
  contributes its deficit (`baseTargetSavings - actualSaved`, only when positive) into a shared
  pool. That pool is split evenly, floor-divided to 2 decimal places, across every milestone that
  is currently pending (deadline not yet passed, not completed) — with any leftover cent added to
  the last receiving milestone so the totals reconcile exactly. If there are no pending milestones
  to receive the pool, it is left unredistributed and nothing changes
  (see `MilestoneCalculator.computeLiveMilestones`).
- A milestone that is itself `COMPLETED` (whether by amount saved or the manual-completion flag)
  never contributes to the deficit pool, even if its deadline has already passed.

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
- Expense list queries default to the last 31 days (today − 30 days through today, inclusive) when
  no date range is supplied. A caller must supply both `from` and `to` or neither — supplying only
  one is rejected. A supplied range spanning 31 or more days between the two dates is rejected (a
  31-day inclusive window passes; 32 days fails) (see `DateRange.resolve`, `ExpenseController`,
  `ExpenseServiceImpl.listExpenses`).
- An expense can optionally link to a savings plan (`planId`). If supplied, the plan must belong to
  the same user, or the request fails with `EXPENSE_PLAN_MISMATCH`. The same error code is used
  whether the plan doesn't exist at all or simply belongs to someone else, so a caller cannot use
  the error response to probe for plan IDs they don't own
  (see `ExpenseServiceImpl.verifyPlanOwnership`).
- Expenses are **soft**-deleted (the row is retained as financial history and excluded from reads),
  following the codebase-wide `BaseEntity` convention. Plans are a deliberate exception: deleting a
  plan physically removes the plan and its milestones from the database, since milestones are
  owned exclusively by their plan (see `ExpenseServiceImpl`, `PlanServiceImpl.deletePlan`,
  `ExpenseController`).

## Expense Categories

- The first time a user requests their expense categories, six system-default categories are
  seeded transactionally and returned: **Rent**, **Utilities**, **Subscriptions** (all `FIXED`),
  and **Groceries**, **Transport**, **Dining out** (all `VARIABLE`) — insertion order is preserved
  in the response (see `ExpenseCategoryServiceImpl.listCategories`,
  `ExpenseCategoryServiceImpl.SYSTEM_DEFAULTS`).
- The `system` flag (serialized as `isSystem`) distinguishes auto-seeded defaults (`true`) from
  user-created categories (`false`), so the frontend can tell the two apart
  (see `ExpenseCategory.system`, `ExpenseCategoryResponse.system`).
- Creating a category rejects a name the user already uses, case-insensitively (e.g. "Groceries"
  collides with "groceries"), returning a 409 `DUPLICATE_CATEGORY_NAME`
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
- Ownership is enforced at the service layer: a plan, milestone, expense, or category that exists
  but isn't owned by the requesting user returns the same "not found"/"forbidden" style response
  used for a genuinely missing resource, so a caller cannot use the response to distinguish
  "doesn't exist" from "exists but isn't yours" (see `PlanServiceImpl.getOwnedPlanOrThrow`,
  `ExpenseServiceImpl.getOwnedExpenseOrThrow`, `ExpenseServiceImpl.verifyPlanOwnership`).

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
  strict JSON (see `GeminiConfig`, `PlanServiceImpl.generateAiPlan`).
