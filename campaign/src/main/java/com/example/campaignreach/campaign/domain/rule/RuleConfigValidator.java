package com.example.campaignreach.campaign.domain.rule;

import com.example.campaignreach.campaign.domain.CampaignType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates a {@link RuleConfig} before it is serialized into the {@code rule_config} JSONB
 * (spec §4, FR-005). Combines three layers and surfaces every failure with a clear reason:
 *
 * <ol>
 *   <li><b>Bean validation</b> (JSR-380): per-field guards such as non-negative discount and
 *       percentage {@code <= 100}.
 *   <li><b>Cross-field consistency</b>: the populated discount value matches {@link DiscountKind},
 *       and {@code minSpend} is present iff {@link ThresholdMode#MIN_SPEND} (FR-003).
 *   <li><b>Type alignment + campaign period</b>: the rule's {@link RuleConfig#campaignType()} must
 *       equal the owning campaign's type, and the campaign {@code endAt} must not be earlier than
 *       {@code startAt}.
 * </ol>
 *
 * <p>The campaign period (endAt &lt; startAt) is checked here rather than on the {@link
 * com.example.campaignreach.campaign.domain.Campaign} aggregate so that create/update routes every
 * rejection (rule + period) through one entry point with a unified reason list, as the task
 * acceptance criteria list the period check under rule validation.
 */
@Component
public class RuleConfigValidator {

    private final Validator beanValidator;

    public RuleConfigValidator(Validator beanValidator) {
        this.beanValidator = beanValidator;
    }

    /** Package-private for unit tests without a Spring context. */
    RuleConfigValidator() {
        this.beanValidator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Validates the rule against its owning campaign's type and active period.
     *
     * @throws RuleConfigValidationException if any rule field, cross-field rule, type alignment or
     *     campaign-period check fails — the exception lists every reason
     */
    public void validate(RuleConfig config, CampaignType campaignType, Instant startAt, Instant endAt) {
        List<String> reasons = new ArrayList<>();

        for (ConstraintViolation<RuleConfig> violation : beanValidator.validate(config)) {
            reasons.add(violation.getMessage());
        }

        if (config.campaignType() != campaignType) {
            reasons.add("ruleConfig type " + config.campaignType() + " does not match campaign type " + campaignType);
        }

        if (config instanceof DiscountRuleConfig discount) {
            collectDiscountReasons(discount, reasons);
        }

        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            reasons.add("campaign endAt must not be earlier than startAt");
        }

        if (!reasons.isEmpty()) {
            throw new RuleConfigValidationException(reasons);
        }
    }

    private void collectDiscountReasons(DiscountRuleConfig discount, List<String> reasons) {
        if (discount.kind() == DiscountKind.AMOUNT) {
            if (discount.amount() == null) {
                reasons.add("discount amount must be provided when kind is AMOUNT");
            }
            if (discount.percentage() != null) {
                reasons.add("discount percentage must be absent when kind is AMOUNT");
            }
        }
        if (discount.kind() == DiscountKind.PERCENTAGE) {
            if (discount.percentage() == null) {
                reasons.add("discount percentage must be provided when kind is PERCENTAGE");
            }
            if (discount.amount() != null) {
                reasons.add("discount amount must be absent when kind is PERCENTAGE");
            }
        }
        if (discount.thresholdMode() == ThresholdMode.MIN_SPEND && discount.minSpend() == null) {
            reasons.add("minSpend must be provided when thresholdMode is MIN_SPEND");
        }
        if (discount.thresholdMode() == ThresholdMode.NONE && discount.minSpend() != null) {
            reasons.add("minSpend must be absent when thresholdMode is NONE");
        }
    }
}
