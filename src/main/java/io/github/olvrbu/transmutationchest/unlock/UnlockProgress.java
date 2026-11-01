package io.github.olvrbu.transmutationchest.unlock;

import io.github.olvrbu.transmutationchest.config.TradeConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A chest's unlock state: how much has been donated per unlock, and which unlocks are complete.
 * Plain Java, so the rules are unit-testable; {@code ChestComponent} stores one of these.
 * <p>
 * Completion is permanent (decided): once an unlock reaches its amount it's recorded in
 * {@link #unlocked()} and stays unlocked even if the config later raises the amount or removes it.
 * Not thread-safe: only used on the chest's world thread.
 */
public final class UnlockProgress {

    private final Map<String, Long> donated = new HashMap<>();
    private final Set<String> unlocked = new LinkedHashSet<>();

    public UnlockProgress() {
    }

    public UnlockProgress(Map<String, Long> donated, Set<String> unlocked) {
        donated.forEach((id, n) -> {
            if (n != null && n > 0) {
                this.donated.put(id, n);
            }
        });
        this.unlocked.addAll(unlocked);
    }

    public UnlockProgress copy() {
        return new UnlockProgress(donated, unlocked);
    }

    public Map<String, Long> donated() {
        return Collections.unmodifiableMap(donated);
    }

    public Set<String> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public long donatedTo(String unlockId) {
        return donated.getOrDefault(unlockId, 0L);
    }

    /**
     * Complete if recorded as unlocked, or if enough was donated for the current config (which can
     * happen when an admin lowered the amount). Unknown unlocks are only complete if recorded.
     */
    public boolean isUnlocked(String unlockId, TradeConfig config) {
        if (unlocked.contains(unlockId)) {
            return true;
        }
        TradeConfig.Unlock unlock = config.unlocks().get(unlockId);
        return unlock != null && donatedTo(unlockId) >= unlock.amount();
    }

    /**
     * Whether this chest may trade the item: for every requirement, at least one of its
     * alternatives is complete (e.g. stone cobble AND any one hardwood log).
     */
    public boolean canTrade(String itemId, TradeConfig config) {
        for (Set<String> anyOf : config.requirementsOf(itemId)) {
            boolean satisfied = false;
            for (String unlockId : anyOf) {
                if (isUnlocked(unlockId, config)) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) {
                return false;
            }
        }
        return config.isTradeable(itemId);
    }

    /** How many more items the unlock needs (0 if complete or not configured). */
    public long remaining(String unlockId, TradeConfig config) {
        TradeConfig.Unlock unlock = config.unlocks().get(unlockId);
        if (unlock == null || isUnlocked(unlockId, config)) {
            return 0;
        }
        return unlock.amount() - donatedTo(unlockId);
    }

    /**
     * Records a donation of {@code quantity} items (callers cap it at {@link #remaining}). Marks the
     * unlock complete when the amount is reached. Returns true if this donation completed it.
     */
    public boolean donate(String unlockId, long quantity, TradeConfig config) {
        if (quantity <= 0 || remaining(unlockId, config) < quantity) {
            throw new IllegalArgumentException("can't donate " + quantity + " to " + unlockId);
        }
        donated.merge(unlockId, quantity, Long::sum);
        return markIfComplete(unlockId, config);
    }

    /** Records the unlock as complete if enough was donated; returns true if it was newly recorded. */
    public boolean markIfComplete(String unlockId, TradeConfig config) {
        if (!unlocked.contains(unlockId) && isUnlocked(unlockId, config)) {
            unlocked.add(unlockId);
            donated.remove(unlockId); // no longer needed once permanent
            return true;
        }
        return false;
    }

    /** Admin/testing: complete an unlock without donations. */
    public void forceUnlock(String unlockId) {
        unlocked.add(unlockId);
        donated.remove(unlockId);
    }

    /** Admin/testing: remove an unlock and its donation progress. */
    public void forceLock(String unlockId) {
        unlocked.remove(unlockId);
        donated.remove(unlockId);
    }
}
