package com.example.campaignreach.campaign.api;

import java.util.List;

/**
 * Error body returned at the campaign API trust boundary (task 4.1).
 *
 * @param error a short machine-readable code (e.g. {@code validation_failed}, {@code not_found},
 *     {@code conflict})
 * @param reasons the human-readable failure reason(s) (FR-005 surfaces these for invalid rules)
 */
public record ApiError(String error, List<String> reasons) {

    /** Defensively copies {@code reasons} to an immutable list so the error body cannot be mutated. */
    public ApiError {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
