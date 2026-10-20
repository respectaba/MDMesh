# Ezi Android agent: command outcome reliability

Fork base: `MDMesh-app/MDMesh` revision `92600ce4d6968f75263a22c2c992c69db52a3d07`.

A repeated command previously returned DONE even if its first execution failed,
was unsupported, or was still in progress. The dispatcher now waits for/replays
its real outcome and completion timestamp. Reusing an id with different content
is refused, while reordered JSON object keys remain equivalent. Cancellation
fences replay; a malformed expiring timestamp cannot bypass the TTL.

Replay is process-local, not durable across restarts. It retains at most 10,000
command identities and 4 MiB of detail data; a full identity cache refuses new
work instead of evicting ids and executing them again. Large replay payloads
retain status/time with an explicit missing-data diagnostic. They do not invent
an empty application inventory. The first response still carries original data.

Local Java17 / Android35 JVM tests and a debug APK build run without paid CI,
release keys or real devices. Three regressions failed on the pinned original
and pass after the fix. This is not a signed release or a fleet rollout. Existing
agent APK checksum and the platform's AGENT_APPLIED_FIELDS remain unchanged.
Stored-only settings, location/removal commands and consented remote view are
subsequent work. The existing signing key is required before publishing an update
that enrolled Device Owner agents can accept.
