package io.github.olvrbu.transmutationchest.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Price math. Plain Java with no server dependencies, so it is unit-testable.
 * <p>
 * All results are {@code long} value units. Every method throws {@link ArithmeticException}
 * instead of silently overflowing.
 */
public final class Pricing {

    private Pricing() {
    }

    /** Balance gained by selling {@code quantity} items with the given base price. */
    public static long sellValue(long basePrice, int quantity) {
        requirePositive(basePrice, "basePrice");
        requireNonNegative(quantity, "quantity");
        return Math.multiplyExact(basePrice, quantity);
    }

    /**
     * Cost of buying one item: {@code ceil(basePrice * (1 + spread))}.
     * <p>
     * The spread is a {@link BigDecimal} (exact decimal) rather than a double: with doubles,
     * {@code 100 * 1.1} is {@code 110.00000000000001}, which would round up to 111.
     */
    public static long buyPrice(long basePrice, BigDecimal spread) {
        requirePositive(basePrice, "basePrice");
        if (spread.signum() < 0) {
            throw new IllegalArgumentException("spread must be >= 0, got " + spread);
        }
        return BigDecimal.valueOf(basePrice)
                .multiply(BigDecimal.ONE.add(spread))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    /** Cost of buying {@code quantity} items. */
    public static long buyCost(long basePrice, BigDecimal spread, int quantity) {
        requireNonNegative(quantity, "quantity");
        return Math.multiplyExact(buyPrice(basePrice, spread), quantity);
    }

    /** New balance after adding {@code amount}. Throws instead of overflowing. */
    public static long credit(long balance, long amount) {
        requireNonNegative(balance, "balance");
        requireNonNegative(amount, "amount");
        return Math.addExact(balance, amount);
    }

    /** New balance after paying {@code amount}, or -1 if the balance doesn't cover it. */
    public static long debit(long balance, long amount) {
        requireNonNegative(balance, "balance");
        requireNonNegative(amount, "amount");
        return amount > balance ? -1 : balance - amount;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + value);
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
    }
}
