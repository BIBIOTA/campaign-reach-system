/**
 * Shared kernel — cross-module event schema (Kafka message contracts).
 *
 * <p>This is the ONLY channel through which campaign and reach communicate (design.md §5). The
 * strongly-typed records here are the stable contract; the topic-name / consumer-group / partition-key
 * contract (task 2.2) lives alongside them.
 *
 * <ul>
 *   <li>{@link com.example.campaignreach.shared.event.ReachRequested} — activity-level, one per
 *       campaign trigger; carries no recipient list.
 *   <li>{@link com.example.campaignreach.shared.event.ReachTaskCreated} — user-level, emitted as the
 *       orchestrator expands a request into N tasks.
 *   <li>{@link com.example.campaignreach.shared.event.SendResultRecorded} — user-level send outcome
 *       from the dispatcher.
 *   <li>{@link com.example.campaignreach.shared.event.KafkaTopics} / {@link
 *       com.example.campaignreach.shared.event.ConsumerGroups} — the three topic names and two
 *       consumer-group ids (design.md §9).
 *   <li>{@link com.example.campaignreach.shared.event.PartitionKeys} — deterministic partition-key
 *       derivation per topic (design.md §9).
 * </ul>
 */
package com.example.campaignreach.shared.event;
