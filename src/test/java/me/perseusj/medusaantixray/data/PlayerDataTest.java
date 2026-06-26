package me.perseusj.medusaantixray.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerDataTest {

    private static final double EPSILON = 1e-9;

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a MineEvent using the backward-compatible factory (v1.1 tests). */
    private static MineEvent event(long ts, boolean valuable, double weight) {
        return MineEvent.of(ts, valuable, weight);
    }

    /** Creates a MineEvent with an explicit Y-level (B1 tests). */
    private static MineEvent eventY(long ts, boolean valuable, double weight, int y) {
        return new MineEvent(ts, valuable, weight, y, 1, false, 0, 0, "UNKNOWN");
    }

    /** Creates a MineEvent with an explicit veinSize (B2 tests). */
    private static MineEvent eventVein(long ts, boolean valuable, double weight, int veinSize) {
        return new MineEvent(ts, valuable, weight, 0, veinSize, false, 0, 0, "UNKNOWN");
    }

    // =========================================================================
    // Existing v1.1 tests (updated to use MineEvent.of() factory)
    // =========================================================================

    @Test
    void ratioMatchesReferenceComputation() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        Random random = new Random(42);
        List<MineEvent> allEvents = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            MineEvent e = event(i, isValuable, weight);
            allEvents.add(e);
            data.addEvent(e);

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
            MineEvent e = event(timestamp, isValuable, weight);
            data.addEvent(e);
            activeEvents.add(e);
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
            loaded.add(event(i * 50L, isValuable, weight));
        }

        List<MineEvent> newEvents = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            boolean isValuable = random.nextBoolean();
            double weight = isValuable ? random.nextDouble() * 2.0 : 0.0;
            MineEvent e = event(20000L + i * 50L, isValuable, weight);
            newEvents.add(e);
            data.addEvent(e);
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

        int liveEventCount   = 50;
        int loadedEventCount = 100;

        List<MineEvent> loaded = new ArrayList<>(loadedEventCount);
        for (int i = 0; i < loadedEventCount; i++) {
            loaded.add(event(i * 10L, true, 1.0));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread addThread = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < liveEventCount; i++) {
                    data.addEvent(event(1_000_000L + i * 10L, true, 1.0));
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

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
        startLatch.countDown();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        assertNull(error.get(), "Unexpected exception: " + error.get());

        int expectedTotal = loadedEventCount + liveEventCount;
        assertEquals(expectedTotal, data.getTotalBlocks(),
                "Expected " + expectedTotal + " total blocks; got " + data.getTotalBlocks()
                + " — events were dropped during concurrent merge.");

        double expectedScore = expectedTotal * 1.0;
        assertEquals(expectedScore, data.calculateScore(), EPSILON,
                "Score does not match expected sum of all event weights.");

        assertTrue(data.isMerged(), "merged flag should be true after mergeLoadedEvents");
    }

    @Test
    void shouldAlertHandlesCooldownCorrectly() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "test");
        long cooldown = 10_000L;

        assertEquals(true,  data.shouldAlert(1000L,  cooldown), "First alert allowed");
        assertEquals(false, data.shouldAlert(9000L,  cooldown), "Within cooldown blocked");
        assertEquals(true,  data.shouldAlert(11001L, cooldown), "After cooldown allowed");
        assertEquals(false, data.shouldAlert(11001L, cooldown), "Same timestamp within cooldown blocked");
        assertEquals(false, data.shouldAlert(11002L, cooldown), "One ms later still within cooldown blocked");
        assertEquals(true,  data.shouldAlert(21002L, cooldown), "After cooldown elapsed, allowed again");
    }

    @Test
    void emptyStateReturnsZero() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "empty");
        assertEquals(0,   data.getTotalBlocks());
        assertEquals(0.0, data.calculateScore(), EPSILON);
        assertEquals(0.0, data.calculateRatio(), EPSILON);
        assertEquals(0,   data.snapshotEvents().size());
    }

    // =========================================================================
    // Phase B tests
    // =========================================================================

    /**
     * B1 — Y-level field is stored correctly on MineEvent and does not affect the
     * scoring algorithm (weight is already pre-multiplied before addEvent is called).
     */
    @Test
    void b1_yLevelFieldPreservedOnEvent() {
        MineEvent e = eventY(1000L, true, 0.75, -58);
        assertEquals(-58, e.y(), "Y-level should be stored on MineEvent");
        assertEquals(0.75, e.weight(), EPSILON, "Weight should be unchanged");

        PlayerData data = new PlayerData(UUID.randomUUID(), "b1-test");
        data.addEvent(e);
        // Score is purely weight-based; y is metadata only.
        assertEquals(0.75, data.calculateScore(), EPSILON);
    }

    /**
     * B1 — Mining at an optimal depth (low weight pre-applied) produces a lower score
     * than the same number of ores at an anomalous depth (high weight pre-applied).
     */
    @Test
    void b1_optimalDepthLowerScoreThanAnomalousDepth() {
        // Simulate 10 diamonds at optimal Y: multiplier 0.5 → weight = 1.5 × 0.5 = 0.75
        PlayerData optimal = new PlayerData(UUID.randomUUID(), "optimal");
        for (int i = 0; i < 10; i++) {
            optimal.addEvent(eventY(i, true, 0.75, -58));   // 1.5 * 0.5
            optimal.addEvent(eventY(i + 1000L, false, 0.0, -58)); // filler
        }

        // Simulate 10 diamonds at anomalous Y: multiplier 2.0 → weight = 1.5 × 2.0 = 3.0
        PlayerData anomalous = new PlayerData(UUID.randomUUID(), "anomalous");
        for (int i = 0; i < 10; i++) {
            anomalous.addEvent(eventY(i, true, 3.0, 50));
            anomalous.addEvent(eventY(i + 1000L, false, 0.0, 50));
        }

        assertTrue(optimal.calculateRatio() < anomalous.calculateRatio(),
                "Optimal-depth mining should produce a lower ratio than anomalous-depth mining");
    }

    /**
     * B2 — VeinContext: new vein starts at veinSize=1; extending it increments veinSize.
     */
    @Test
    void b2_veinContextGrowsCorrectly() {
        VeinContext ctx = new VeinContext("DIAMOND_ORE", 0, -58, 0, 1000L);
        assertEquals(1, ctx.getVeinSize());
        assertEquals("DIAMOND_ORE", ctx.getMaterialName());

        ctx.extend(1, -58, 0, 1050L);
        assertEquals(2, ctx.getVeinSize());
        assertEquals(1050L, ctx.getLastTimestampMs());
    }

    /**
     * B2 — VeinContext: Chebyshev distance calculation.
     */
    @Test
    void b2_chebyshevDistanceIsCorrect() {
        VeinContext ctx = new VeinContext("GOLD_ORE", 5, 10, 5, 1000L);
        assertEquals(0, ctx.chebyshevDistance(5, 10, 5));   // same block
        assertEquals(3, ctx.chebyshevDistance(8, 10, 5));   // 3 in X
        assertEquals(3, ctx.chebyshevDistance(5, 13, 8));   // 3 in Y and Z → max=3
        assertEquals(5, ctx.chebyshevDistance(0, 10, 5));   // 5 in X
    }

    /**
     * B2 — "divide" mode: a 4-block vein (each with weight/4) contributes far less than
     * 4 isolated ores at full weight.
     */
    @Test
    void b2_veinDivideModeLowersTotalScore() {
        double baseWeight = 1.5;

        // 4 isolated ores
        PlayerData isolated = new PlayerData(UUID.randomUUID(), "isolated");
        for (int i = 0; i < 4; i++) {
            isolated.addEvent(MineEvent.of(i, true, baseWeight));
        }

        // 4 blocks in a vein (divide mode: weight/veinSize per block)
        PlayerData vein = new PlayerData(UUID.randomUUID(), "vein");
        for (int i = 1; i <= 4; i++) {
            vein.addEvent(eventVein(i, true, baseWeight / i, i));
        }

        assertTrue(vein.calculateScore() < isolated.calculateScore(),
                "Vein-grouped ores should have a lower total score than the same number of isolated ores. "
                + "Vein score=" + vein.calculateScore() + " vs isolated=" + isolated.calculateScore());
    }

    /**
     * B2 — "first-only" mode: only the first ore in a vein is scored; subsequent blocks
     * become non-valuable (filler-like) and only increase the denominator.
     */
    @Test
    void b2_veinFirstOnlyModeScoredAsOne() {
        double baseWeight = 1.5;

        // first-only: 1 valuable + 3 non-valuable (isValuable=false)
        PlayerData data = new PlayerData(UUID.randomUUID(), "first-only");
        data.addEvent(MineEvent.of(1L, true,  baseWeight)); // first block
        data.addEvent(MineEvent.of(2L, false, 0.0));        // vein block 2
        data.addEvent(MineEvent.of(3L, false, 0.0));        // vein block 3
        data.addEvent(MineEvent.of(4L, false, 0.0));        // vein block 4

        assertEquals(baseWeight, data.calculateScore(), EPSILON,
                "Only the first ore block should contribute to the score");
        assertEquals(4, data.getTotalBlocks(),
                "All 4 blocks must count in the denominator");
        assertEquals(baseWeight / 4.0, data.calculateRatio(), EPSILON,
                "Ratio should reflect 1 scored ore out of 4 total blocks");
    }

    /**
     * B3 — OreWeight record stores and exposes its fields correctly.
     */
    @Test
    void b3_oreWeightRecordFieldsAccessible() {
        OreWeight w = new OreWeight(1.5, 0.4);
        assertEquals(1.5, w.hiddenWeight(), EPSILON);
        assertEquals(0.4, w.exposedWeight(), EPSILON);
    }

    /**
     * B3 — Applying per-ore weights: a diamond (1.5/0.4) contributes more than iron (0.6/0.15)
     * when mined hidden, producing different ratios with the same ore count.
     */
    @Test
    void b3_perOreWeightsProduceDifferentScores() {
        OreWeight diamond = new OreWeight(1.5, 0.4);
        OreWeight iron    = new OreWeight(0.6, 0.15);

        PlayerData dData = new PlayerData(UUID.randomUUID(), "diamond");
        PlayerData iData = new PlayerData(UUID.randomUUID(), "iron");

        for (int i = 0; i < 10; i++) {
            dData.addEvent(MineEvent.of(i, true,  diamond.hiddenWeight()));
            iData.addEvent(MineEvent.of(i, true,  iron.hiddenWeight()));
            // Same filler count
            dData.addEvent(MineEvent.of(i + 1000L, false, 0.0));
            iData.addEvent(MineEvent.of(i + 1000L, false, 0.0));
        }

        assertTrue(dData.calculateRatio() > iData.calculateRatio(),
                "Hidden diamonds should produce a higher suspicion ratio than hidden iron. "
                + "diamond=" + dData.calculateRatio() + " iron=" + iData.calculateRatio());
    }

    /**
     * B4 — MineEvent stores enchantment fields correctly (structural test).
     */
    @Test
    void b4_mineEventStoresEnchantmentFields() {
        MineEvent e = new MineEvent(5000L, true, 0.75,
                -58, 1, true, 3, 5, "DIAMOND_PICKAXE");
        assertTrue(e.hasSilkTouch());
        assertEquals(3, e.fortuneLevel());
        assertEquals(5, e.efficiencyLevel());
        assertEquals("DIAMOND_PICKAXE", e.toolType());
    }

    /**
     * B4 — Silk Touch weight reduction: applying the silk-touch multiplier (0.5) to the base
     * hidden weight produces a lower score than without it.
     */
    @Test
    void b4_silkTouchReducesScore() {
        double hiddenWeight = 1.5;
        double silkMult     = 0.5;

        PlayerData silkUser = new PlayerData(UUID.randomUUID(), "silk");
        PlayerData plainUser = new PlayerData(UUID.randomUUID(), "plain");

        for (int i = 0; i < 10; i++) {
            silkUser.addEvent(MineEvent.of(i, true,  hiddenWeight * silkMult));
            plainUser.addEvent(MineEvent.of(i, true, hiddenWeight));
            silkUser.addEvent(MineEvent.of(i + 1000L, false, 0.0));
            plainUser.addEvent(MineEvent.of(i + 1000L, false, 0.0));
        }

        assertTrue(silkUser.calculateRatio() < plainUser.calculateRatio(),
                "Silk Touch miner should have lower ratio. "
                + "silk=" + silkUser.calculateRatio() + " plain=" + plainUser.calculateRatio());
    }

    /**
     * B4 — Fortune III reduces weight via pow(0.8, 3) ≈ 0.512.
     */
    @Test
    void b4_fortuneIIIReducesScoreByExpectedFactor() {
        double hiddenWeight = 1.5;
        double fortuneMult  = Math.pow(0.8, 3); // ≈ 0.512

        PlayerData fortune = new PlayerData(UUID.randomUUID(), "fortune");
        PlayerData plain   = new PlayerData(UUID.randomUUID(), "plain");

        for (int i = 0; i < 10; i++) {
            fortune.addEvent(MineEvent.of(i, true, hiddenWeight * fortuneMult));
            plain.addEvent(MineEvent.of(i, true,   hiddenWeight));
            fortune.addEvent(MineEvent.of(i + 1000L, false, 0.0));
            plain.addEvent(MineEvent.of(i + 1000L, false, 0.0));
        }

        assertTrue(fortune.calculateRatio() < plain.calculateRatio(),
                "Fortune III miner should have lower ratio than unenchanted miner.");

        double expectedFortune = (hiddenWeight * fortuneMult * 10) / 20.0;
        assertEquals(expectedFortune, fortune.calculateRatio(), 1e-6,
                "Fortune ratio should match pow(0.8,3) reduction.");
    }
}