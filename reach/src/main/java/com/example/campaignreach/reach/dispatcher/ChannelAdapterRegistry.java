package com.example.campaignreach.reach.dispatcher;

import com.example.campaignreach.reach.channel.ChannelAdapter;
import com.example.campaignreach.shared.event.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Routes a {@link Channel} to its {@link ChannelAdapter} (task 9.1, spec §4 FR-009). The dispatcher
 * looks up the adapter whose {@link ChannelAdapter#channel()} matches a claimed task's channel — the
 * Open/Closed extension point: a new channel means a new adapter bean, never a change here.
 *
 * <p>Adapters are gated behind their provider bindings ({@code EmailAdapter} registers only once an
 * {@code EmailProviderClient} exists), so a channel may legitimately have no adapter yet; lookup
 * returns {@link Optional#empty()} rather than failing construction.
 */
@Component
public final class ChannelAdapterRegistry {

    private final Map<Channel, ChannelAdapter> byChannel = new EnumMap<>(Channel.class);

    /**
     * @param adapters all registered {@link ChannelAdapter} beans (may be empty until a provider binds)
     * @throws IllegalStateException if two adapters claim the same channel (ambiguous routing)
     */
    public ChannelAdapterRegistry(List<ChannelAdapter> adapters) {
        for (ChannelAdapter adapter : adapters) {
            ChannelAdapter previous = byChannel.put(adapter.channel(), adapter);
            if (previous != null) {
                throw new IllegalStateException("two ChannelAdapters registered for channel " + adapter.channel());
            }
        }
    }

    /**
     * @param channel the delivery channel of a claimed task
     * @return the matching adapter, or empty if no adapter is registered for that channel
     */
    public Optional<ChannelAdapter> forChannel(Channel channel) {
        return Optional.ofNullable(byChannel.get(channel));
    }
}
