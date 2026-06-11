package com.pos.ocr.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Currency-safe monetary value backed by {@link BigDecimal}, preserving the printed scale.
 * Never use double for money — that is the whole point of this class.
 *
 * <p>The scale is NOT normalized at construction: a fuel price of 3.499 or a KWD amount with
 * three decimals must survive parsing intact, because the merchant's rounding convention is
 * unknown until validation time. Rounding is an explicit policy decision made via
 * {@link #rounded(int, RoundingMode)}; equality and comparison are scale-agnostic
 * ({@code 5 == 5.00}).
 */
public final class Money implements Comparable<Money> {

    /** Floor tolerance: one cent. Aggregate checks scale this up with line count. */
    public static final Money EPSILON = Money.of("0.01");
    public static final Money ZERO = Money.of("0.00");

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount;
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    public static Money of(double value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor));
    }

    public Money abs() {
        return new Money(this.amount.abs());
    }

    /**
     * Round to an explicit printed scale. Used when validation materializes a computed value
     * onto the document (e.g. a self-healed line total), never implicitly.
     */
    public Money rounded(int scale, RoundingMode mode) {
        return new Money(this.amount.setScale(scale, mode));
    }

    public BigDecimal value() {
        return amount;
    }

    /** True when |this - other| <= epsilon. The core comparison for tolerant math checks. */
    public boolean equalsWithin(Money other, Money epsilon) {
        return this.subtract(other).abs().amount.compareTo(epsilon.amount) <= 0;
    }

    @Override
    public int compareTo(Money o) {
        return this.amount.compareTo(o.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
