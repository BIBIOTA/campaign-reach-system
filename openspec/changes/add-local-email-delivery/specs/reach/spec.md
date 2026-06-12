## ADDED Requirements

### Requirement: 本機 SMTP Email provider shall deliver EMAIL reach tasks to Mailpit

The system SHALL provide a local-only SMTP `EmailProviderClient` implementation that sends EMAIL reach tasks through Spring Mail to Mailpit when explicitly enabled by the `local` profile and `campaignreach.email-provider.mode=smtp-local`.

#### Scenario: Local SMTP provider is disabled outside explicit local mode

- **WHEN** the application starts without the `local` profile
- **THEN** the system does not register the local SMTP `EmailProviderClient`
- **AND** the existing provider-gated `EmailAdapter` behavior remains unchanged

> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

#### Scenario: Local SMTP provider activates when local mode is complete

- **WHEN** the application starts with the `local` profile, `campaignreach.email-provider.mode=smtp-local`, and valid SMTP host, port, from, and recipient settings
- **THEN** the system registers the local SMTP `EmailProviderClient`
- **AND** the existing `EmailAdapter` becomes available for EMAIL dispatch through the channel adapter registry

> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

#### Scenario: EMAIL reach task is sent to Mailpit

- **WHEN** dispatcher claims an EMAIL `reach_task` and creates `ReachMessage(userId, EMAIL, templateRef)`
- **THEN** the system renders a local test email from that message
- **AND** sends it through Spring Mail to Mailpit SMTP on the configured host and port
- **AND** marks the task as `SENT` after the SMTP send succeeds

> See: ../../diagrams/01-sequence-local-email-delivery-flow.puml
> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

### Requirement: Local email template shall be simple and deterministic

The system SHALL render local-only email content from `ReachMessage` without requiring a formal marketing template engine or a registered template catalog.

#### Scenario: Template renderer produces a local test message

- **WHEN** the renderer receives `ReachMessage(userId, EMAIL, templateRef)`
- **THEN** the subject includes `[Local Campaign Reach]` and the provided `templateRef`
- **AND** the body includes a local test notice, `templateRef`, `userId`, channel, and send time

> See: ../../diagrams/01-sequence-local-email-delivery-flow.puml

#### Scenario: Unknown templateRef uses the generic local template

- **WHEN** `templateRef` is any non-blank value not known to the system
- **THEN** the system still renders the generic local test email
- **AND** does not fail dispatch because the template is unknown

### Requirement: Local SMTP configuration shall fail fast when invalid

The system SHALL validate local SMTP settings at application startup so local smoke tests fail loudly before dispatch begins.

#### Scenario: Invalid local SMTP configuration fails startup

- **WHEN** local SMTP mode is enabled and SMTP host, from address, recipient address, or port is missing or invalid
- **THEN** the application context fails during configuration binding or bean creation
- **AND** no partially configured local SMTP provider is registered

> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

#### Scenario: SMTP transport failure follows retryable provider path

- **WHEN** Mailpit is unavailable, SMTP times out, or the SMTP client reports a transient transport error during send
- **THEN** the provider failure is surfaced through the existing `EmailAdapter` retryable failure path
- **AND** dispatcher retry and circuit breaker behavior remain responsible for backoff and degradation

> See: ../../diagrams/01-sequence-local-email-delivery-flow.puml

### Requirement: Local email delivery shall preserve reach PII boundaries

The system SHALL keep recipient email addresses out of persisted reach data and events even when local SMTP delivery is enabled.

#### Scenario: Fixed local recipient is not persisted

- **WHEN** the local SMTP provider sends an EMAIL reach task
- **THEN** the recipient email address is read only from local provider configuration
- **AND** the system does not write that address to `reach_task`, Kafka events, `ReachMessage`, metrics API responses, or audit trail records

> See: ../../diagrams/01-sequence-local-email-delivery-flow.puml
> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

#### Scenario: Provider logs remain conservative

- **WHEN** local SMTP delivery succeeds or fails
- **THEN** provider logs do not include full email message content
- **AND** operational logs are limited to identifiers such as provider message id and task or user identifiers when needed

### Requirement: Local development environment shall include Mailpit smoke-test support

The system SHALL provide local development configuration and documentation that starts Mailpit with the rest of the local infrastructure and explains how to verify a captured email.

#### Scenario: Docker compose starts Mailpit with local infrastructure

- **WHEN** a developer runs `docker compose up -d`
- **THEN** PostgreSQL, Kafka, and Mailpit are started for local development
- **AND** Mailpit exposes SMTP port `1025` and Web UI port `8025`

> See: ../../diagrams/02-component-local-smtp-email-architecture.puml

#### Scenario: Local smoke test is documented

- **WHEN** a developer follows README local email smoke-test instructions
- **THEN** they can load `.env`, start the app in local SMTP mode, trigger an EMAIL reach flow, and view the captured email at `http://localhost:8025`
- **AND** the documentation states that Mailpit captures local mail and does not send to real external recipients

> See: ../../diagrams/01-sequence-local-email-delivery-flow.puml
