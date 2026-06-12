package com.example.campaignreach.reach.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.campaignreach.shared.event.Channel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fast unit tests for {@link LocalEmailTemplateRenderer} — the local-only smoke-test email template
 * renderer (task 2.1; spec requirement「Local email template shall be simple and deterministic」;
 * design.md §信件樣板).
 *
 * <p>Test names map to the spec scenarios for traceability. The send time is passed in so the assertions
 * are deterministic and no hidden {@code Instant.now()} is exercised.
 */
class LocalEmailTemplateRendererTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final Instant SENT_AT = Instant.parse("2026-06-12T09:30:00Z");

    private final LocalEmailTemplateRenderer renderer = new LocalEmailTemplateRenderer();

    @Test
    @DisplayName("Template renderer produces a local test message")
    void templateRendererProducesLocalTestMessage() {
        ReachMessage message = new ReachMessage(USER_ID, Channel.EMAIL, "summer-sale-email");

        RenderedEmail rendered = renderer.render(message, SENT_AT);

        // Subject includes the local marker and the provided templateRef.
        assertThat(rendered.subject()).contains("[Local Campaign Reach]").contains("summer-sale-email");

        // Body includes a local-test notice, templateRef, userId, channel, and send time.
        assertThat(rendered.body())
                .containsIgnoringCase("local test")
                .contains("summer-sale-email")
                .contains(USER_ID.toString())
                .contains(Channel.EMAIL.toString())
                .contains(SENT_AT.toString());
    }

    @Test
    @DisplayName("Unknown templateRef uses the generic local template")
    void unknownTemplateRefUsesTheGenericLocalTemplate() {
        // An arbitrary, never-registered but non-blank templateRef.
        ReachMessage message =
                new ReachMessage(USER_ID, Channel.EMAIL, "totally-unknown-template-xyz-" + UUID.randomUUID());

        RenderedEmail rendered = renderer.render(message, SENT_AT);

        // Does not fail; still renders the generic local template echoing the unknown ref.
        assertThat(rendered.subject()).contains("[Local Campaign Reach]").contains(message.templateRef());
        assertThat(rendered.body()).containsIgnoringCase("local test").contains(message.templateRef());
    }

    @Test
    @DisplayName("Blank templateRef is rejected before rendering")
    void blankTemplateRefIsRejectedBeforeRendering() {
        // The ReachMessage invariant rejects a null/blank templateRef, so an invalid message can never be
        // constructed and therefore never reaches the renderer — the renderer does not paper over it.
        assertThatThrownBy(() -> new ReachMessage(USER_ID, Channel.EMAIL, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ReachMessage(USER_ID, Channel.EMAIL, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
