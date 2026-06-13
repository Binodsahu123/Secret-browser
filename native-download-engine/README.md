# Native Download Engine Component

High-performance native JNI implementation designed to schedule file chunks, coordinate download threads, assemble file parts, and calculate validation hashes.

## Optimized Mechanics
1. **Multi-Thread Chunk Splicing**: Natively partitions downloading assets into strict, non-overlapping byte range offsets to ensure thread alignments without collision vectors.
2. **Sequential Block Disk Assemblers**: Combines multiple download chunk fragments into final outputs natively via 64KB block merging. This lowers disk I/O seek delay overhead and limits background memory footprints.
3. **High-Throughput IO Verifiers**: Validates file blocks directly from raw file descriptors to ensure complete download integrity without JVM heap load.
