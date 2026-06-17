# Native Security Engine Component

High-performance native JNI implementation designed to lock sandbox boundaries, scan bytes for known malicious payloads, and enforce address boundaries.

## Optimized Mechanics
1. **Posix Jail Path Resolvers**: Runs strict canonical matching logic on paths natively to identify symlink bypasses, relative parent traversals (`..`), and directory context leaks.
2. **Infinite NOP Slide Scanning**: Scans executable binaries, assets, and script payloads byte-by-byte for infinite NOP loops or shellcodes prior to execution.
3. **Restricted Token Namespace Verification**: Natively checks extension identifiers and namespace headers to defend system processes from dynamic register corruption.
