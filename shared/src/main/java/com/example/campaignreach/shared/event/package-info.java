/**
 * Shared kernel — cross-module event schema (Kafka message contracts).
 *
 * <p>This is the ONLY channel through which campaign and reach communicate (design.md §5). The
 * strongly-typed records here are the stable contract; topic names, partition keys and serializer
 * wiring belong to task 2.2.
 *
 * <ul>
 *   <li>{@link com.example.campaignreach.shared.event.ReachRequested} — activity-level, one per
 *       campaign trigger; carries no recipient list.
 *   <li>{@link com.example.campaignreach.shared.event.ReachTaskCreated} — user-level, emitted as the
 *       orchestrator expands a request into N tasks.
 *   <li>{@link com.example.campaignreach.shared.event.SendResultRecorded} — user-level send outcome
 *       from the dispatcher.
 * </ul>
 */
package com.example.campaignreach.shared.event;
