package com.example.campaignreach.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Fail-fast acceptance for required infrastructure connectivity (design.md §3, §9):
 * a missing Kafka broker or DB URL must fail startup with an explicit error,
 * never a silent degradation.
 */
class RequiredInfrastructurePropertiesValidatorTest {

    private MockEnvironment fullyConfiguredEnvironment() {
        return new MockEnvironment()
                .withProperty(
                        RequiredInfrastructurePropertiesValidator.KAFKA_BOOTSTRAP_SERVERS,
                        "localhost:9092")
                .withProperty(
                        RequiredInfrastructurePropertiesValidator.DATASOURCE_URL,
                        "jdbc:postgresql://localhost:5432/campaignreach");
    }

    @Test
    void startupSucceedsWhenAllRequiredPropertiesPresent() {
        var validator =
                new RequiredInfrastructurePropertiesValidator(fullyConfiguredEnvironment());

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void startupFailsWhenKafkaBrokerMissing() {
        var environment = fullyConfiguredEnvironment();
        environment.withProperty(
                RequiredInfrastructurePropertiesValidator.KAFKA_BOOTSTRAP_SERVERS, "");
        var validator = new RequiredInfrastructurePropertiesValidator(environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(MissingInfrastructureConfigException.class)
                .hasMessageContaining(
                        RequiredInfrastructurePropertiesValidator.KAFKA_BOOTSTRAP_SERVERS)
                .extracting(ex -> ((MissingInfrastructureConfigException) ex).getMissingProperties())
                .asList()
                .containsExactly(
                        RequiredInfrastructurePropertiesValidator.KAFKA_BOOTSTRAP_SERVERS);
    }

    @Test
    void startupFailsWhenDatasourceUrlMissing() {
        var environment = fullyConfiguredEnvironment();
        environment.withProperty(RequiredInfrastructurePropertiesValidator.DATASOURCE_URL, "");
        var validator = new RequiredInfrastructurePropertiesValidator(environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(MissingInfrastructureConfigException.class)
                .hasMessageContaining(RequiredInfrastructurePropertiesValidator.DATASOURCE_URL);
    }

    @Test
    void startupFailureListsEveryMissingPropertyAtOnce() {
        var validator =
                new RequiredInfrastructurePropertiesValidator(new MockEnvironment());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(MissingInfrastructureConfigException.class)
                .satisfies(ex -> assertThat(
                                ((MissingInfrastructureConfigException) ex).getMissingProperties())
                        .containsExactlyInAnyOrder(
                                RequiredInfrastructurePropertiesValidator.KAFKA_BOOTSTRAP_SERVERS,
                                RequiredInfrastructurePropertiesValidator.DATASOURCE_URL));
    }
}
