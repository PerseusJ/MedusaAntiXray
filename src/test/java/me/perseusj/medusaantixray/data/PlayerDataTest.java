package me.perseusj.medusaantixray.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PlayerDataTest {

    private static final double EPSILON = 1e-9;

    @Test
    void ratioMatchesReferenceComputation() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        Random random = new Random(42);
        List<MineEvent> allEvents = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            MineEvent event = new MineEvent(i, isValuable, weight);
            allEvents.add(event);
            data.addEvent(event);

            double refScore = allEvents.stream().filter(MineEvent::isValuable).mapToDouble(MineEvent::weight).sum();
            assertEquals(refScore, data.calculateScore(), EPSILON, "Score mismatch at event " + i);
            assertEquals(allEvents.size(), data.getTotalBlocks(), "Total blocks mismatch at event " + i);

            double refRatio = allEvents.isEmpty() ? 0 : refScore / allEvents.size();
            assertEquals(refRatio, data.calculateRatio(), EPSILON, "Ratio mismatch at event " + i);
        }
    }

    @Test
    void purgeExpiredMaintainsConsistency() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        Random random = new Random(99);
        List<MineEvent> activeEvents = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            long timestamp = i * 100L;
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            MineEvent event = new MineEvent(timestamp, isValuable, weight);
            data.addEvent(event);
            activeEvents.add(event);
        }

        long cutoff = 200 * 100L;
        data.purgeExpired(cutoff);
        activeEvents.removeIf(e -> e.timestamp() < cutoff);

        double refScore = activeEvents.stream().filter(MineEvent::isValuable).mapToDouble(MineEvent::weight).sum();
        assertEquals(refScore, data.calculateScore(), EPSILON, "Score after purge");
        assertEquals(activeEvents.size(), data.getTotalBlocks(), "Total blocks after purge");

        double refRatio = activeEvents.isEmpty() ? 0 : refScore / activeEvents.size();
        assertEquals(refRatio, data.calculateRatio(), EPSILON, "Ratio after purge");
    }

    @Test
    void mergeLoadedEventsPreservesConsistency() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        Random random = new Random(7);
        List<MineEvent> loaded = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            loaded.add(new MineEvent(i * 50L, isValuable, weight));
        }

        List<MineEvent> newEvents = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            MineEvent event = new MineEvent(20000L + i * 50L, isValuable, weight);
            newEvents.add(event);
            data.addEvent(event);
        }

        data.mergeLoadedEvents(loaded);

        List<MineEvent> combined = new ArrayList<>();
        combined.addAll(loaded);
        combined.addAll(newEvents);
        combined.sort(java.util.Comparator.comparingLong(MineEvent::timestamp));

        double refScore = combined.stream().filter(MineEvent::isValuable).mapToDouble(MineEvent::weight).sum();
        assertEquals(refScore, data.calculateScore(), EPSILON, "Score after merge");
        assertEquals(combined.size(), data.getTotalBlocks(), "Total blocks after merge");

        double refRatio = combined.isEmpty() ? 0 : refScore / combined.size();
        assertEquals(refRatio, data.calculateRatio(), EPSILON, "Ratio after merge");
    }

    @Test
    void shouldAlertHandlesCooldownCorrectly() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        long cooldown = 10_000L;

        assertEquals(true, data.shouldAlert(1000L, cooldown), "First alert allowed");
        assertEquals(false, data.shouldAlert(9000L, cooldown), "Within cooldown blocked");
        assertEquals(true, data.shouldAlert(11001L, cooldown), "After cooldown allowed");
        assertEquals(false, data.shouldAlert(11001L, cooldown), "Same timestamp within cooldown blocked");
        assertEquals(false, data.shouldAlert(11002L, cooldown), "One ms later still within cooldown blocked");
        assertEquals(true, data.shouldAlert(21002L, cooldown), "After cooldown elapsed, allowed again");
    }

    @Test
    void emptyStateReturnsZero() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "empty");
        assertEquals(0, data.getTotalBlocks());
        assertEquals(0.0, data.calculateScore(), EPSILON);
        assertEquals(0.0, data.calculateRatio(), EPSILON);
        assertEquals(0, data.snapshotEvents().size());
    }
}