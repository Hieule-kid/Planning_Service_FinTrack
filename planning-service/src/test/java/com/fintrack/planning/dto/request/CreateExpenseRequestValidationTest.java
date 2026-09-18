package com.fintrack.planning.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for {@link CreateExpenseRequest} — the constraints the
 * controller enforces via {@code @Valid} before the service is reached.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class CreateExpenseRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void amountOfZero_isRejected() {
        CreateExpenseRequest request = validRequest();
        request.setAmount(BigDecimal.ZERO);

        assertThat(validator.validate(request))
                .anyMatch(v -> "amount".equals(v.getPropertyPath().toString()));
    }

    @Test
    void validRequest_hasNoViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    private CreateExpenseRequest validRequest() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setAmount(new BigDecimal("0.01"));
        request.setCurrency("USD");
        request.setCategoryId("cat-1");
        request.setSpentOn(LocalDate.now());
        return request;
    }
}
