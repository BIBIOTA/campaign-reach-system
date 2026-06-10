package com.example.campaignreach.campaign.api;

import com.example.campaignreach.campaign.domain.rule.RuleConfigValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the campaign API's domain/validation exceptions to HTTP responses (task 4.1).
 *
 * <ul>
 *   <li>{@link RuleConfigValidationException} / bean-validation failure → 400 with the reasons (FR-005)
 *   <li>{@link CampaignNotFoundException} → 404
 *   <li>{@link ObjectOptimisticLockingFailureException} → 409 (concurrent stale-version edit, FR-001)
 * </ul>
 *
 * <p>Authentication (401) and authorization (403) are handled by Spring Security upstream and are
 * deliberately not caught here.
 */
@RestControllerAdvice(assignableTypes = CampaignController.class)
public class CampaignApiExceptionHandler {

    @ExceptionHandler(RuleConfigValidationException.class)
    public ResponseEntity<ApiError> handleRuleValidation(RuleConfigValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", ex.getReasons()));
    }

    /** Maps bean-validation failures on request DTOs to 400 with per-field reasons. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<String> reasons = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("validation_failed", reasons));
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(CampaignNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("not_found", List.of(ex.getMessage())));
    }

    /** Maps a concurrent stale-version edit to 409 so the later writer fails rather than overwrites (FR-001). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "conflict",
                        List.of("campaign was modified concurrently; re-read and retry with the latest version")));
    }
}
