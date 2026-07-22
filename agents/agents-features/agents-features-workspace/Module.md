# agents-features-workspace

Beta contracts and coordination primitives for long-running, externally supervised agent runs.

The module provides typed input requests, extensible content and artifact references, replayable
workspace events, restart-safe interruption records, explicit run outcomes, and cooperative
cancellation. It integrates with Koog Persistence checkpoints without introducing another graph
state store.

Decision receipts with an expiry are rejected during revalidation and checked again while atomically
claiming a resume, so a process restart or delayed worker cannot revive stale authority.
