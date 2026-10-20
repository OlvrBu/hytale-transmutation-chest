package io.github.obut.tradestation.unlock;

import io.github.obut.tradestation.config.TradeConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockProgressTest {

    private static TradeConfig config(long stoneAmount) {
        return new TradeConfig(BigDecimal.ZERO,
                Map.of("Cobble", 10L, "Brick", 10L, "Table", 40L, "Free", 1L, "Planks", 5L),
                Map.of("Stone", new TradeConfig.Unlock(stoneAmount, List.of("Cobble")),
                        "Wood", new TradeConfig.Unlock(100, List.of("Log")),
                        "Birch", new TradeConfig.Unlock(100, List.of("BirchLog"))),
                Map.of("Cobble", List.of(Set.of("Stone")), "Brick", List.of(Set.of("Stone")),
                        "Table", List.of(Set.of("Stone"), Set.of("Wood")),
                        "Planks", List.of(Set.of("Wood", "Birch"))));
    }

    @Test
    void lockedUntilAmountReached() {
        var config = config(500);
        var progress = new UnlockProgress();
        assertFalse(progress.canTrade("Brick", config));
        assertTrue(progress.canTrade("Free", config)); // no requirements
        assertFalse(progress.donate("Stone", 499, config));
        assertEquals(1, progress.remaining("Stone", config));
        assertTrue(progress.donate("Stone", 1, config));
        assertTrue(progress.canTrade("Brick", config));
        assertEquals(0, progress.remaining("Stone", config));
    }

    @Test
    void itemsNeedingSeveralUnlocksNeedAll() {
        var config = config(5);
        var progress = new UnlockProgress();
        progress.donate("Stone", 5, config);
        assertFalse(progress.canTrade("Table", config));
        progress.donate("Wood", 100, config);
        assertTrue(progress.canTrade("Table", config));
    }

    @Test
    void cannotOverDonate() {
        var config = config(5);
        var progress = new UnlockProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.donate("Stone", 6, config));
        progress.donate("Stone", 5, config);
        assertThrows(IllegalArgumentException.class, () -> progress.donate("Stone", 1, config));
        assertThrows(IllegalArgumentException.class, () -> progress.donate("Unknown", 1, config));
    }

    @Test
    void unlocksStayWhenConfigRaisesAmount() {
        var progress = new UnlockProgress();
        progress.donate("Stone", 500, config(500));
        assertTrue(progress.isUnlocked("Stone", config(5000)));
        assertTrue(progress.unlocked().contains("Stone"));
    }

    @Test
    void lowerAmountCompletesExistingProgress() {
        var progress = new UnlockProgress(Map.of("Stone", 300L), Set.of());
        assertFalse(progress.isUnlocked("Stone", config(500)));
        assertTrue(progress.isUnlocked("Stone", config(200)));
        assertTrue(progress.markIfComplete("Stone", config(200)));
        assertTrue(progress.isUnlocked("Stone", config(500))); // now permanent
    }

    @Test
    void forceUnlockAndLock() {
        var config = config(500);
        var progress = new UnlockProgress(Map.of("Stone", 100L), Set.of());
        progress.forceUnlock("Stone");
        assertTrue(progress.canTrade("Cobble", config));
        progress.forceLock("Stone");
        assertFalse(progress.canTrade("Cobble", config));
        assertEquals(500, progress.remaining("Stone", config));
    }

    @Test
    void anyOfRequirementsNeedJustOne() {
        var config = config(5);
        var progress = new UnlockProgress();
        assertFalse(progress.canTrade("Planks", config));
        progress.donate("Birch", 100, config);
        assertTrue(progress.canTrade("Planks", config));
    }
}
