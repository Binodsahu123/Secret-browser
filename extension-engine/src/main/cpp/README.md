# Native Extension Runtime Component

High-performance native JNI implementation designed to optimize Extension Event Loops, routing tables, and script matching criteria.

## Optimized Mechanics
1. **O(1) Sparse Bitmask Permissions**: Bypasses traditional linear loop matching (`allowed.contains()`) by indexing allowed extension endpoints into a fast C++ sparse `unordered_set` structure.
2. **Zero-Pauses Event Routing**: Message envelopes are allocated on the direct native thread stack frame instead of the JVM heap space, avoiding Garbage Collection allocation and compaction cycles.
3. **Atomic Listener Iterators**: Loops rapid events to active listeners natively with sub-nanosecond delay steps using standard C++ clock cycles.
