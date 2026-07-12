package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.request.ToggleRecalculateRequest;
import com.fintrack.planning.dto.request.UpdateMilestoneRequest;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.model.enums.Currency;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.PlanCategory;
import com.fintrack.planning.model.enums.TimeframeCategory;
import com.fintrack.planning.repository.MilestoneRepository;
import com.fintrack.planning.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlanServiceImpl} — covers CRUD orchestration, ownership
 * enforcement, normalisation of FE-shaped requests, and milestone lifecycle ops.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceImplTest {

    private static final String USER_ID  = "user-1";
    private static final String OTHER_ID = "user-2";
    private static final String PLAN_ID  = "plan-1";
    private static final String MS_ID    = "ms-1";

    @Mock private PlanRepository planRepository;
    @Mock private MilestoneRepository milestoneRepository;

    @InjectMocks private PlanServiceImpl planService;

    // ─────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private PlanEntity defaultPlan;

    @BeforeEach
    void setUp() {
        defaultPlan = PlanEntity.builder()
                .userId(USER_ID)
                .goalTitle("Emergency Fund")
                .targetAmount(new BigDecimal("600.00"))
                .currency(Currency.VND)
                .planCategory(PlanCategory.EMERGENCY)
                .timeframeCategory(TimeframeCategory.SHORT_TERM)
                .durationInMonths(6)
                .frequency(Frequency.MONTHLY)
                .requiredPerPeriod(new BigDecimal("100.00"))
                .startDate(LocalDate.of(2027, 1, 1))
                .recalculateOnMissedDeadline(false)
                .build();
        defaultPlan.setId(PLAN_ID);
    }

    /** Builds a minimal milestone attached to {@code defaultPlan}. */
    private MilestoneEntity buildMilestone(String id, BigDecimal base, BigDecimal saved, LocalDate deadline) {
        MilestoneEntity ms = MilestoneEntity.builder()
                .baseTargetSavings(base)
                .targetSavings(base)
                .actualSaved(saved)
                .manuallyCompleted(false)
                .deadline(deadline)
                .periodDate(deadline.withDayOfMonth(1))
                .timeline("Test")
                .sequenceIndex(0)
                .build();
        ms.setId(id);
        ms.setPlan(defaultPlan);
        return ms;
    }

    /** Returns a minimal valid {@link CreatePlanRequest} that does not need normalisation. */
    private CreatePlanRequest validRequest() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setGoalTitle("Emergency Fund");
        req.setTargetAmount(new BigDecimal("600.00"));
        req.setCurrency(Currency.VND);
        req.setPlanCategory(PlanCategory.EMERGENCY);
        req.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        req.setDurationInMonths(6);
        req.setFrequency(Frequency.MONTHLY);
        req.setRequiredPerPeriod(new BigDecimal("100.00"));
        req.setStartDate(LocalDate.of(2027, 1, 1));
        return req;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createPlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createPlan_savesAndReturnsPlanResponse() {
        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PlanResponse response = planService.createPlan(USER_ID, validRequest());

        assertThat(response.getId()).isEqualTo(PLAN_ID);
        assertThat(response.getGoalTitle()).isEqualTo("Emergency Fund");
        assertThat(response.getMilestones()).hasSize(6);
        verify(planRepository).save(any(PlanEntity.class));
        verify(milestoneRepository).saveAll(anyList());
    }

    @Test
    void createPlan_defaultsStartDateToTodayWhenNull() {
        CreatePlanRequest req = validRequest();
        req.setStartDate(null);

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void createPlan_copiesDurationToDurationInMonthsWhenDurationInMonthsAbsent() {
        CreatePlanRequest req = validRequest();
        req.setDurationInMonths(null);
        req.setDuration(6);

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getDurationInMonths()).isEqualTo(6);
    }

    @Test
    void createPlan_derivesShortTermCategoryFrom6Months() {
        CreatePlanRequest req = validRequest();
        req.setTimeframeCategory(null);
        req.setDurationInMonths(6); // ≤11 → SHORT_TERM

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeframeCategory()).isEqualTo(TimeframeCategory.SHORT_TERM);
    }

    @Test
    void createPlan_derivesMidTermCategoryFrom24Months() {
        CreatePlanRequest req = validRequest();
        req.setTimeframeCategory(null);
        req.setDurationInMonths(24); // 12–60 → MID_TERM
        req.setTargetAmount(new BigDecimal("2400.00"));
        req.setRequiredPerPeriod(new BigDecimal("100.00"));

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeframeCategory()).isEqualTo(TimeframeCategory.MID_TERM);
    }

    @Test
    void createPlan_derivesLongTermCategoryFrom72Months() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setGoalTitle("Retirement");
        req.setTargetAmount(new BigDecimal("7200.00"));
        req.setCurrency(Currency.VND);
        req.setFrequency(Frequency.MONTHLY);
        req.setRequiredPerPeriod(new BigDecimal("100.00"));
        req.setStartDate(LocalDate.of(2027, 1, 1));
        req.setDurationInMonths(72); // >60 → LONG_TERM
        // timeframeCategory intentionally null

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeframeCategory()).isEqualTo(TimeframeCategory.LONG_TERM);
    }

    @Test
    void createPlan_derivesLongTermCategoryFromDurationInYears() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setGoalTitle("House");
        req.setTargetAmount(new BigDecimal("10000.00"));
        req.setCurrency(Currency.VND);
        req.setFrequency(Frequency.ANNUALLY);
        req.setRequiredPerPeriod(new BigDecimal("5000.00"));
        req.setStartDate(LocalDate.of(2027, 1, 1));
        req.setDurationInYears(2);
        // both timeframeCategory and durationInMonths intentionally null

        when(planRepository.save(any(PlanEntity.class))).thenAnswer(inv -> {
            PlanEntity p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });
        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        planService.createPlan(USER_ID, req);

        ArgumentCaptor<PlanEntity> captor = ArgumentCaptor.forClass(PlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeframeCategory()).isEqualTo(TimeframeCategory.LONG_TERM);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listPlans
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void listPlans_emptyRepository_returnsEmptyList() {
        when(planRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        List<PlanSummaryResponse> result = planService.listPlans(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void listPlans_returnsSummaryForEachPlan() {
        when(planRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(defaultPlan));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID))
                .thenReturn(List.of());

        List<PlanSummaryResponse> result = planService.listPlans(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(PLAN_ID);
        assertThat(result.get(0).getGoalTitle()).isEqualTo("Emergency Fund");
    }

    @Test
    void listPlans_computesTotalsFromMilestones() {
        MilestoneEntity completed = buildMilestone("ms-1", new BigDecimal("100.00"),
                new BigDecimal("100.00"), LocalDate.of(2027, 1, 31));

        when(planRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(defaultPlan));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID))
                .thenReturn(List.of(completed));

        List<PlanSummaryResponse> result = planService.listPlans(USER_ID);

        PlanSummaryResponse summary = result.get(0);
        assertThat(summary.getTotalSaved()).isEqualByComparingTo("100.00");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPlan_returnsPlanResponse() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of());

        PlanResponse response = planService.getPlan(USER_ID, PLAN_ID);

        assertThat(response.getId()).isEqualTo(PLAN_ID);
        assertThat(response.getGoalTitle()).isEqualTo("Emergency Fund");
    }

    @Test
    void getPlan_throwsNotFoundWhenPlanAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.getPlan(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getPlan_throwsNotFoundWhenPlanIsSoftDeleted() {
        defaultPlan.setDeleted(true);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        assertThatThrownBy(() -> planService.getPlan(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getPlan_throwsForbiddenWhenWrongOwner() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        assertThatThrownBy(() -> planService.getPlan(OTHER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateMilestoneActualSaved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateMilestoneActualSaved_updatesAmountAndReturnsResponse() {
        MilestoneEntity ms = buildMilestone(MS_ID, new BigDecimal("100.00"),
                BigDecimal.ZERO, LocalDate.of(2099, 12, 31));

        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.of(ms));
        when(milestoneRepository.save(ms)).thenReturn(ms);
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of(ms));

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("75.00"));

        PlanResponse response = planService.updateMilestoneActualSaved(USER_ID, PLAN_ID, MS_ID, req);

        assertThat(ms.getActualSaved()).isEqualByComparingTo("75.00");
        verify(milestoneRepository).save(ms);
        assertThat(response.getId()).isEqualTo(PLAN_ID);
    }

    @Test
    void updateMilestoneActualSaved_throwsNotFoundWhenPlanAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("50.00"));

        assertThatThrownBy(() -> planService.updateMilestoneActualSaved(USER_ID, PLAN_ID, MS_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void updateMilestoneActualSaved_throwsNotFoundWhenMilestoneAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.empty());

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("50.00"));

        assertThatThrownBy(() -> planService.updateMilestoneActualSaved(USER_ID, PLAN_ID, MS_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void updateMilestoneActualSaved_throwsForbiddenWhenWrongOwner() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("50.00"));

        assertThatThrownBy(() -> planService.updateMilestoneActualSaved(OTHER_ID, PLAN_ID, MS_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // completeMilestone
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void completeMilestone_setsActualSavedToEffectiveTargetWhenBelow() {
        MilestoneEntity ms = buildMilestone(MS_ID, new BigDecimal("100.00"),
                new BigDecimal("30.00"), LocalDate.of(2099, 12, 31));

        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.of(ms));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of(ms));
        when(milestoneRepository.save(ms)).thenReturn(ms);

        planService.completeMilestone(USER_ID, PLAN_ID, MS_ID);

        assertThat(ms.getActualSaved()).isEqualByComparingTo("100.00");
        assertThat(ms.isManuallyCompleted()).isTrue();
    }

    @Test
    void completeMilestone_doesNotReduceActualSavedWhenAboveTarget() {
        MilestoneEntity ms = buildMilestone(MS_ID, new BigDecimal("100.00"),
                new BigDecimal("150.00"), LocalDate.of(2099, 12, 31));

        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.of(ms));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of(ms));
        when(milestoneRepository.save(ms)).thenReturn(ms);

        planService.completeMilestone(USER_ID, PLAN_ID, MS_ID);

        // actualSaved was already above target — must not be lowered to 100
        assertThat(ms.getActualSaved()).isEqualByComparingTo("150.00");
        assertThat(ms.isManuallyCompleted()).isTrue();
    }

    @Test
    void completeMilestone_setsManuallyCompletedFlag() {
        MilestoneEntity ms = buildMilestone(MS_ID, new BigDecimal("100.00"),
                BigDecimal.ZERO, LocalDate.of(2099, 12, 31));

        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.of(ms));
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of(ms));
        when(milestoneRepository.save(ms)).thenReturn(ms);

        planService.completeMilestone(USER_ID, PLAN_ID, MS_ID);

        assertThat(ms.isManuallyCompleted()).isTrue();
        verify(milestoneRepository).save(ms);
    }

    @Test
    void completeMilestone_throwsNotFoundWhenPlanAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.completeMilestone(USER_ID, PLAN_ID, MS_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void completeMilestone_throwsNotFoundWhenMilestoneAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.completeMilestone(USER_ID, PLAN_ID, MS_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // undoMilestone
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void undoMilestone_clearsManuallyCompletedFlag() {
        MilestoneEntity ms = buildMilestone(MS_ID, new BigDecimal("100.00"),
                new BigDecimal("100.00"), LocalDate.of(2099, 12, 31));
        ms.setManuallyCompleted(true);

        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.of(ms));
        when(milestoneRepository.save(ms)).thenReturn(ms);
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of(ms));

        planService.undoMilestone(USER_ID, PLAN_ID, MS_ID);

        assertThat(ms.isManuallyCompleted()).isFalse();
        verify(milestoneRepository).save(ms);
    }

    @Test
    void undoMilestone_throwsForbiddenWhenWrongOwner() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        assertThatThrownBy(() -> planService.undoMilestone(OTHER_ID, PLAN_ID, MS_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void undoMilestone_throwsNotFoundWhenMilestoneAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(milestoneRepository.findByIdAndPlanId(MS_ID, PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.undoMilestone(USER_ID, PLAN_ID, MS_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateSettings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateSettings_enablesRedistribution() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(planRepository.save(defaultPlan)).thenReturn(defaultPlan);
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of());

        ToggleRecalculateRequest req = new ToggleRecalculateRequest();
        req.setRecalculateOnMissedDeadline(true);

        PlanResponse response = planService.updateSettings(USER_ID, PLAN_ID, req);

        assertThat(defaultPlan.isRecalculateOnMissedDeadline()).isTrue();
        verify(planRepository).save(defaultPlan);
        assertThat(response.getId()).isEqualTo(PLAN_ID);
    }

    @Test
    void updateSettings_disablesRedistribution() {
        defaultPlan.setRecalculateOnMissedDeadline(true);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(planRepository.save(defaultPlan)).thenReturn(defaultPlan);
        when(milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(PLAN_ID)).thenReturn(List.of());

        ToggleRecalculateRequest req = new ToggleRecalculateRequest();
        req.setRecalculateOnMissedDeadline(false);

        planService.updateSettings(USER_ID, PLAN_ID, req);

        assertThat(defaultPlan.isRecalculateOnMissedDeadline()).isFalse();
    }

    @Test
    void updateSettings_throwsForbiddenWhenWrongOwner() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        ToggleRecalculateRequest req = new ToggleRecalculateRequest();
        req.setRecalculateOnMissedDeadline(true);

        assertThatThrownBy(() -> planService.updateSettings(OTHER_ID, PLAN_ID, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deletePlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deletePlan_deletesMilestonesAndSoftDeletesPlan() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));
        when(planRepository.save(defaultPlan)).thenReturn(defaultPlan);

        planService.deletePlan(USER_ID, PLAN_ID);

        verify(milestoneRepository).deleteByPlanId(PLAN_ID);
        assertThat(defaultPlan.isDeleted()).isTrue();
        verify(planRepository).save(defaultPlan);
    }

    @Test
    void deletePlan_throwsNotFoundWhenPlanAbsent() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.deletePlan(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(milestoneRepository, never()).deleteByPlanId(anyString());
    }

    @Test
    void deletePlan_throwsForbiddenWhenWrongOwner() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        assertThatThrownBy(() -> planService.deletePlan(OTHER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(milestoneRepository, never()).deleteByPlanId(anyString());
    }

    @Test
    void deletePlan_throwsNotFoundWhenPlanAlreadyDeleted() {
        defaultPlan.setDeleted(true);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(defaultPlan));

        assertThatThrownBy(() -> planService.deletePlan(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
