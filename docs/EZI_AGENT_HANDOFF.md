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

## Managed policy handlers (source only)

The next source change adds `policy.systemUpdatePolicy` and
`policy.passwordRequirements`. Update modes are default/automatic/windowed/
postponed; windows use device-local time and Android's postponement limits.
Password payloads accept only quality/minimumLength, never a new passcode or a
failed-attempt wipe threshold. Setting requirements does not prove that the
person's current credential already meets them.

Advertisement is per device and read on each collection. Update/password policy
requires Device Owner; password additionally requires a secure lock screen and
excludes automotive builds. Owner-only remote actions disappear when authority
is absent, while classic active administrators may still lock. Passcode reset
is advertised only on API26+ with an active locally registered token. The two
small upstream ownership-probe commits are explicitly cherry-picked with credit;
no unrelated upstream release is merged.

The public provisioning APK currently served by mdmesh.eziaba.com is version 0.3.4
(code 304), signed with certificate SHA256
`3f4b357285e895f0f0bd90fe5fa0cd1d530cdad55a48cf6888097d508302c5fc`;
it matches the platform's QR signature pin. Installed versions may differ per
device/tenant. The new fork has no signing secret; no Android signing reference
was found among the dedicated project's registered secret names. An update
requires the matching private signing key and a higher version code. Before a
release, audit parity with the currently served agent; this pinned-source debug
build is not an approved downgrade or a replacement fleet APK.

Native platform configuration translation, protocol integration and signed
staging acceptance remain required. Existing platform checksum and enforced-field
labels have not changed. Display/volume/launcher settings, fetch-location,
removal and consented remote view remain subsequent work.

Qualification for managed-policy source: 84 JVM tests (core/policy/protocol) and debug APK build pass. No Android instrumentation or enrolled-device acceptance was run.

## Provisioning-agent 0.3.4 source parity

Imported only agent-android and proto changes from upstream v0.3.4 onto the
pinned integration ancestry. This restores protocol 1.4 config application,
persisted desired state and reapplication, install-integrity handling and Wi-Fi
failsafe behavior already present in the public provisioning release. Ezi's
command replay safeguards, managed update/password handlers and live authority
probes remain intact. Deferred policies produce a failed/non-applied outcome.
Regression coverage checks that changing device authority updates the next
capability matrix without mutating an earlier snapshot.

Local core/policy/protocol tests, debug APK assembly and app/core/policy Android
lint pass. Instrumentation tests were compiled, not executed on hardware. No
release signing, platform capability-label change, APK replacement or device
command occurred. Matching release-key custody remains an external dependency.
