package com.wafgateway.worklet1.signature;

/**
 * How dangerous a signature match is, from least to most severe.
 *
 * <p>Used by {@code Worklet1} to decide whether a match should actually
 * block a request (via {@code block.minSeverity} in config) — a LOW-severity
 * hint might just be logged, while a CRITICAL match always blocks.
 *
 * @author Worklet 1 Team
 */
public enum Severity {
    /** Worth recording but rarely worth blocking on its own. */
    LOW,
    /** Suspicious enough to usually warrant blocking. */
    MEDIUM,
    /** A strong, specific attack signature. */
    HIGH,
    /** Severe enough to always block, regardless of configured threshold. */
    CRITICAL
}
