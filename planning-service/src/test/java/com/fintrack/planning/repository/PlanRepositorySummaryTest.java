package com.fintrack.planning.repository;

import com.fintrack.planning.config.JpaAuditingConfig;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.model.enums.Currency;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.MilestoneStatus;
import com.fintrack.planning.model.enums.TimeframeCategory;
import com.fintrack.planning.service.impl.MilestoneCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link PlanRepository#summarizeByUser(String)} against a real PostgreSQL, because the query
 * is native (LATERAL join). Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class PlanRepositorySummaryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String USER = "user-1";
    private static final String OTHER = "user-2";
    // Fixed "today" so the equivalence test is deterministic: milestones due before it are OVERDUE.
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Autowired private PlanRepository planRepository;
    @Autowired private MilestoneRepository milestoneRepository;

    @Test
    void emptyForUserWithNoPlans() {
        assertThat(planRepository.summarizeByUser(USER)).isEmpty();
    }

    @Test
    void singleCurrency_countsOnlyCompletedMilestonesAsSaved() {
        PlanEntity plan = plan(USER, Currency.VND, "1000.00", false);
        // completed by amount, partially saved (not counted), completed manually below its base target
        milestone(plan, 0, "300.00", "300.00", false);
        milestone(plan, 1, "300.00", "100.00", false);
        milestone(plan, 2, "400.00", "150.00", true);

        List<CurrencyTotals> rows = planRepository.summarizeByUser(USER);

        assertThat(rows).hasSize(1);
        CurrencyTotals vnd = rows.get(0);
        assertThat(vnd.getCurrency()).isEqualTo("VND");
        assertThat(vnd.getPlanCount()).isEqualTo(1);
        assertThat(vnd.getCompletedPlanCount()).isZero();
        assertThat(vnd.getTotalTarget()).isEqualByComparingTo("1000.00");
        assertThat(vnd.getTotalSaved()).isEqualByComparingTo("450.00");
        assertThat(vnd.getRemaining()).isEqualByComparingTo("550.00");
    }

    @Test
    void planWithoutMilestonesProgress_hasZeroSavedAndFullRemaining() {
        PlanEntity plan = plan(USER, Currency.USD, "500.00", false);
        milestone(plan, 0, "500.00", "0.00", false);

        CurrencyTotals usd = planRepository.summarizeByUser(USER).get(0);

        assertThat(usd.getTotalSaved()).isEqualByComparingTo("0.00");
        assertThat(usd.getRemaining()).isEqualByComparingTo("500.00");
    }

    @Test
    void mixedCurrencies_areNeverCombined() {
        PlanEntity vnd = plan(USER, Currency.VND, "10000000000.00", false);
        milestone(vnd, 0, "10000000000.00", "10000000000.00", false);
        PlanEntity usd = plan(USER, Currency.USD, "500.00", false);
        milestone(usd, 0, "500.00", "100.00", false);

        List<CurrencyTotals> rows = planRepository.summarizeByUser(USER);

        assertThat(rows).extracting(CurrencyTotals::getCurrency).containsExactly("USD", "VND");
        CurrencyTotals usdRow = rows.get(0);
        CurrencyTotals vndRow = rows.get(1);
        assertThat(usdRow.getTotalTarget()).isEqualByComparingTo("500.00");
        assertThat(usdRow.getTotalSaved()).isEqualByComparingTo("0.00");
        assertThat(vndRow.getTotalTarget()).isEqualByComparingTo("10000000000.00");
        assertThat(vndRow.getTotalSaved()).isEqualByComparingTo("10000000000.00");
        assertThat(vndRow.getCompletedPlanCount()).isEqualTo(1);
    }

    @Test
    void overSavedPlan_doesNotOffsetAnotherPlansShortfall() {
        PlanEntity overSaved = plan(USER, Currency.VND, "100.00", false);
        milestone(overSaved, 0, "100.00", "250.00", false);
        PlanEntity behind = plan(USER, Currency.VND, "100.00", false);
        milestone(behind, 0, "100.00", "0.00", false);

        CurrencyTotals vnd = planRepository.summarizeByUser(USER).get(0);

        assertThat(vnd.getTotalSaved()).isEqualByComparingTo("250.00");
        assertThat(vnd.getRemaining()).isEqualByComparingTo("100.00");
        assertThat(vnd.getCompletedPlanCount()).isEqualTo(1);
    }

    @Test
    void softDeletedPlansAndMilestones_areExcluded() {
        PlanEntity live = plan(USER, Currency.VND, "1000.00", false);
        milestone(live, 0, "500.00", "500.00", false);
        MilestoneEntity deletedMilestone = milestone(live, 1, "500.00", "500.00", false);
        deletedMilestone.setDeleted(true);
        milestoneRepository.saveAndFlush(deletedMilestone);

        PlanEntity deletedPlan = plan(USER, Currency.VND, "9999.00", false);
        milestone(deletedPlan, 0, "9999.00", "9999.00", false);
        deletedPlan.setDeleted(true);
        planRepository.saveAndFlush(deletedPlan);

        CurrencyTotals vnd = planRepository.summarizeByUser(USER).get(0);

        assertThat(vnd.getPlanCount()).isEqualTo(1);
        assertThat(vnd.getTotalTarget()).isEqualByComparingTo("1000.00");
        assertThat(vnd.getTotalSaved()).isEqualByComparingTo("500.00");
    }

    @Test
    void otherUsersData_doesNotLeak() {
        PlanEntity mine = plan(USER, Currency.VND, "100.00", false);
        milestone(mine, 0, "100.00", "100.00", false);
        PlanEntity theirs = plan(OTHER, Currency.VND, "7777.00", false);
        milestone(theirs, 0, "7777.00", "7777.00", false);

        CurrencyTotals vnd = planRepository.summarizeByUser(USER).get(0);

        assertThat(vnd.getPlanCount()).isEqualTo(1);
        assertThat(vnd.getTotalTarget()).isEqualByComparingTo("100.00");
        assertThat(vnd.getTotalSaved()).isEqualByComparingTo("100.00");
        assertThat(planRepository.summarizeByUser("nobody")).isEmpty();
    }

    /**
     * The SQL must agree with what each plan card shows (MilestoneCalculator.computeTotals), including
     * when deficit redistribution is on and milestones are overdue/pending/partial.
     */
    @Test
    void matchesPerPlanCardTotals_withAndWithoutRedistribution() {
        for (boolean recalc : new boolean[] {false, true}) {
            String user = "equiv-" + recalc;
            PlanEntity p = plan(user, Currency.VND, "1200.00", recalc);
            // overdue + partial, overdue + untouched, exactly on target, manually completed under target,
            // future pending + partial, future pending untouched
            milestone(p, 0, "200.00", "50.00", false, TODAY.minusMonths(5));
            milestone(p, 1, "200.00", "0.00", false, TODAY.minusMonths(4));
            milestone(p, 2, "200.00", "200.00", false, TODAY.minusMonths(3));
            milestone(p, 3, "200.00", "80.00", true, TODAY.minusMonths(2));
            milestone(p, 4, "200.00", "150.00", false, TODAY.plusMonths(1));
            milestone(p, 5, "200.00", "0.00", false, TODAY.plusMonths(2));

            List<MilestoneEntity> milestones = milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(p.getId());
            var live = MilestoneCalculator.computeLiveMilestones(milestones, recalc, TODAY);
            var cardTotals = MilestoneCalculator.computeTotals(live, p.getTargetAmount());

            CurrencyTotals row = planRepository.summarizeByUser(user).get(0);

            assertThat(row.getTotalSaved()).as("totalSaved, recalc=%s", recalc)
                    .isEqualByComparingTo(cardTotals.totalSaved());
            assertThat(row.getRemaining()).as("remaining, recalc=%s", recalc)
                    .isEqualByComparingTo(cardTotals.remaining());
            assertThat(live).extracting(l -> l.status() == MilestoneStatus.COMPLETED).contains(true, false);
        }
    }

    private PlanEntity plan(String userId, Currency currency, String target, boolean recalc) {
        return planRepository.saveAndFlush(PlanEntity.builder()
                .userId(userId)
                .goalTitle("Goal")
                .targetAmount(new BigDecimal(target))
                .currency(currency)
                .timeframeCategory(TimeframeCategory.SHORT_TERM)
                .frequency(Frequency.MONTHLY)
                .requiredPerPeriod(BigDecimal.ONE)
                .startDate(TODAY)
                .recalculateOnMissedDeadline(recalc)
                .build());
    }

    private MilestoneEntity milestone(PlanEntity plan, int index, String base, String actual, boolean manual) {
        return milestone(plan, index, base, actual, manual, TODAY.plusMonths(index));
    }

    private MilestoneEntity milestone(PlanEntity plan, int index, String base, String actual, boolean manual,
                                      LocalDate deadline) {
        return milestoneRepository.saveAndFlush(MilestoneEntity.builder()
                .plan(plan)
                .sequenceIndex(index)
                .timeline("M" + index)
                .periodDate(deadline.withDayOfMonth(1))
                .deadline(deadline)
                .baseTargetSavings(new BigDecimal(base))
                .targetSavings(new BigDecimal(base))
                .actualSaved(new BigDecimal(actual))
                .manuallyCompleted(manual)
                .build());
    }
}
