package com.fintrack.planning.service;

import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;

import java.util.List;

/**
 * Service contract for managing a user's expense categories.
 *
 * <p>Every method is scoped to a single authenticated user; callers must always
 * pass the requesting user's ID.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public interface ExpenseCategoryService {

    /**
     * Lists the user's expense categories. If the user has none yet, a fixed set
     * of six system defaults is seeded (in the same transaction) and returned.
     *
     * @param userId the owning user's ID
     * @return the user's categories
     */
    List<ExpenseCategoryResponse> listCategories(String userId);

    /**
     * Creates a new user-defined category.
     *
     * @param userId  the owning user's ID
     * @param request the validated creation payload
     * @return the created category
     * @throws com.fintrack.core.exception.AppException with
     *         {@link com.fintrack.core.exception.ErrorCode#DUPLICATE_CATEGORY_NAME}
     *         if the user already has a category with this name (case-insensitive)
     */
    ExpenseCategoryResponse createCategory(String userId, CreateExpenseCategoryRequest request);
}
