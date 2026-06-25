package me.perseusj.medusaantixray.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

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

    /**
     * A2 — Concurrency test: {@link PlayerData#addEvent} and {@link PlayerData#mergeLoadedEvents}
     * running on two threads must not drop any events from the final count.
     */
    @Test
    void mergeLoadedEventsDoesNotDropNewEvents() throws InterruptedException {
        PlayerData data = new PlayerData(UUID.randomUUID(), "concurrent-test");

        int liveEventCount = 50;
        int loadedEventCount = 100;

        // Pre-populate some "loaded" events (older timestamps).
        List<MineEvent> loaded = new ArrayList<>(loadedEventCount);
        for (int i = 0; i < loadedEventCount; i++) {
            loaded.add(new MineEvent(i * 10L, true, 1.0));
        }

        // Barrier so both threads start as close together as possible.
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // Thread 1: simulate the main-thread block-break events arriving concurrently with the DB load.
        Thread addThread = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < liveEventCount; i++) {
                    // Timestamps are after the loaded events so they are newer.
                    data.addEvent(new MineEvent(1_000_000L + i * 10L, true, 1.0));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: simulate the DatabaseManager async callback calling mergeLoadedEvents.
        Thread mergeThread = new Thread(() -> {
            try {
                startLatch.await();
                data.mergeLoadedEvents(loaded);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        addThread.start();
        mergeThread.start();
        startLatch.countDown(); // Release both threads simultaneously.

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        assertNull(error.get(), "Unexpected exception: " + error.get());

        // After both threads complete, the total block count must equal the union of all events.
        int expectedTotal = loadedEventCount + liveEventCount;
        assertEquals(expectedTotal, data.getTotalBlocks(),
                "Expected " + expectedTotal + " total blocks; got " + data.getTotalBlocks()
                + " — events were dropped during concurrent merge.");

        double expectedScore = expectedTotal * 1.0; // all events have weight=1.0 and isValuable=true
        assertEquals(expectedScore, data.calculateScore(), EPSILON,
                "Score does not match expected sum of all event weights.");

        assertTrue(data.isMerged(), "merged flag should be true after mergeLoadedEvents");
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