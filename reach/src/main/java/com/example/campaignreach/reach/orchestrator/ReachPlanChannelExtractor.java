package com.example.campaignreach.reach.orchestrator;

import com.example.campaignreach.shared.event.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads the delivery {@link Channel} out of the frozen {@code reach_plan_snapshot} JSON at the reach
 * trust boundary (task 7.3, spec §5).
 *
 * <p>The {@code reach_plan} JSON shape is {@code {"channel":"EMAIL","templateRef":"...","timing":"SCHEDULED"}}.
 * Fan-out needs the {@code channel} because it is the fourth column of the {@code reach_task} dedup key
 * {@code unique(campaign_id, user_id, send_cycle_key, channel)}. This is deliberately minimal — it
 * reads only {@code channel} and ignores {@code templateRef}/{@code timing} (those belong to the
 * dispatcher in section 9) — mirroring how {@link com.example.campaignreach.reach.audience.TargetSpecParser}
 * turns one untrusted JSON snapshot into one typed value.
 */
@Component
public class ReachPlanChannelExtractor {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper the application JSON mapper (defensively copied so later reconfiguration of
     *     the shared instance cannot perturb parsing)
     */
    public ReachPlanChannelExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    /**
     * Extracts the {@link Channel} from a {@code reach_plan} JSON snapshot.
     *
     * @param json the raw {@code reach_plan} JSON the event froze
     * @return the parsed delivery channel
     * @throws IllegalArgumentException when the JSON is blank, malformed, not an object, or carries a
     *     missing/blank/unknown {@code channel}
     */
    public Channel extract(String json) {
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
        JsonNode channelNode = root.path("channel");
        String raw = channelNode.isTextual() ? channelNode.asText().trim() : "";
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("reachPlan.channel must not be blank");
        }
        try {
            return Channel.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown reachPlan.channel: " + raw, e);
        }
    }
}
