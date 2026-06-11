package com.example.campaignreach.reach.orchestrator;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for paged audience fan-out into {@code reach_task} (task 7.3, spec §5).
 *
 * <p>Two independent knobs, intentionally kept separate because they serve different concerns:
 *
 * <ul>
 *   <li>{@code pageSize} ({@code campaignreach.reach.expansion.page-size}) — how many recipients are
 *       resolved-and-inserted per committed page. Smaller pages commit progress more often (finer
 *       crash-resume granularity) at the cost of more round trips; larger pages batch harder. This is
 *       a throughput / resume-granularity lever, not a correctness one.
 *   <li>{@code frequencyCapWindow} ({@code campaignreach.reach.frequency-cap.window}) — the lookback
 *       window for frequency capping (a {@link Duration}). Before inserting a recipient's task, the
 *       expander skips the user if they already have a reach_task in a <em>different</em> send cycle
 *       within this window. This is a deliberate over-reach guard, completely separate from the
 *       four-column unique-constraint idempotency (which dedups the <em>same</em> cycle).
 * </ul>
 *
 * @param expansion paged fan-out batch sizing ({@code campaignreach.reach.expansion.*})
 * @param frequencyCap frequency-cap lookback window ({@code campaignreach.reach.frequency-cap.*})
 */
@ConfigurationProperties(prefix = "campaignreach.reach")
public record ExpansionProperties(Expansion expansion, FrequencyCap frequencyCap) {

    /** Applies sensible defaults so the expander is usable without explicit configuration. */
    public ExpansionProperties {
        expansion = expansion == null ? new Expansion(null) : expansion;
        frequencyCap = frequencyCap == null ? new FrequencyCap(null) : frequencyCap;
    }

    /** @return the validated page size (default 1000). */
    public int pageSize() {
        return expansion.pageSize();
    }

    /** @return the frequency-cap lookback window (default PT24H). */
    public Duration frequencyCapWindow() {
        return frequencyCap.window();
    }

    /**
     * Paged fan-out batch sizing.
     *
     * @param pageSize recipients per committed page (positive; default 1000)
     */
    public record Expansion(Integer pageSize) {

        /** Defaults the page size to 1000 and rejects non-positive overrides. */
        public Expansion {
            int resolved = pageSize == null ? 1000 : pageSize;
            if (resolved <= 0) {
                throw new IllegalArgumentException("campaignreach.reach.expansion.page-size must be positive");
            }
            pageSize = resolved;
        }
    }

    /**
     * Frequency-cap lookback window.
     *
     * @param window lookback {@link Duration} over historical reach_task (default PT24H)
     */
    public record FrequencyCap(Duration window) {

        /** Defaults the window to 24h and rejects a non-positive override. */
        public FrequencyCap {
            window = window == null ? Duration.ofHours(24) : window;
            if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("campaignreach.reach.frequency-cap.window must be positive");
            }
        }
    }
}
