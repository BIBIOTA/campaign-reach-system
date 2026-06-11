package com.example.campaignreach.reach.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code templateRef} out of the frozen {@code reach_plan_snapshot} JSON at dispatch time
 * (task 9.1, spec §5).
 *
 * <p>The {@code reach_plan} JSON shape is {@code {"channel":"EMAIL","templateRef":"...","timing":"SCHEDULED"}}.
 * Fan-out (task 7.3) already consumed {@code channel} via
 * {@link com.example.campaignreach.reach.orchestrator.ReachPlanChannelExtractor}; the dispatcher
 * consumes {@code templateRef} here to build the {@link
 * com.example.campaignreach.reach.channel.ReachMessage}. Deliberately minimal and mirrors the channel
 * extractor: one untrusted JSON snapshot in, one typed value out.
 */
@Component
public class ReachPlanTemplateExtractor {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper the application JSON mapper (defensively copied so later reconfiguration of
     *     the shared instance cannot perturb parsing)
     */
    public ReachPlanTemplateExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    /**
     * Extracts the {@code templateRef} from a {@code reach_plan} JSON snapshot.
     *
     * @param json the raw {@code reach_plan} JSON the event froze
     * @return the parsed template reference
     * @throws IllegalArgumentException when the JSON is blank, malformed, not an object, or carries a
     *     missing/blank {@code templateRef}
     */
    public String extract(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("reachPlan JSON must not be null or blank");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("reachPlan JSON is malformed: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("reachPlan JSON must be a JSON object");
        }
        JsonNode templateNode = root.path("templateRef");
        String raw = templateNode.isTextual() ? templateNode.asText().trim() : "";
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("reachPlan.templateRef must not be blank");
        }
        return raw;
    }
}
