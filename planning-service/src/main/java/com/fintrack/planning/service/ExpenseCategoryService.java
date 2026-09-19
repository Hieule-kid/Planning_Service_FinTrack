package com.fintrack.planning.service;

import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;

import java.util.List;

/**
 * Service contract for managing a user's expense categories.
 *
 */
public interface ExpenseCategoryService {

    List<ExpenseCategoryResponse> listCategories(String userId);

    ExpenseCategoryResponse createCategory(String userId, CreateExpenseCategoryRequest request);
}
