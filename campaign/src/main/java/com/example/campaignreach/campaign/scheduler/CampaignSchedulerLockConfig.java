package com.example.campaignreach.campaign.scheduler;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires distributed scheduler locking for the campaign module's {@code @Scheduled} sweeps (task 6.2,
 * §5, US-004). Lives in {@code campaign.scheduler} so the module owns its own scheduling
 * infrastructure (mirroring {@link CampaignSchedulingConfig}), rather than relying on the
 * {@code :app} bootstrap.
 *
 * <p>{@link EnableSchedulerLock} activates the {@code @SchedulerLock} aspect; the {@link LockProvider}
 * bean is backed by a {@link JdbcTemplateLockProvider} over the application {@link DataSource} (the
 * standard {@code shedlock} table is created by Flyway migration {@code V3__shedlock.sql}). Together
 * with the deterministic {@code send_cycle_key} derived in {@link CampaignReachScanScheduler}, the
 * same activity + same schedule cycle runs only once across multiple instances or restart
 * back-scans — not missed, not duplicated.
 *
 * <p>{@code defaultLockAtMostFor} bounds how long a lock is held if a node dies mid-sweep without
 * releasing it (a safety ceiling, not the expected hold time); it is set comfortably above one sweep.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class CampaignSchedulerLockConfig {

    /**
     * JDBC-backed lock provider over the application DataSource. Uses {@code TIMESTAMP}-based locking
     * against the standard ShedLock table.
     *
     * @param dataSource the application DataSource (provided by spring-boot-starter-data-jpa)
     * @return a JdbcTemplate-backed {@link LockProvider}
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
