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

## Managed display settings (source only)

Adds strict Device Owner handlers for brightness, screen timeout and media-stream
volume. Brightness/timeout require API28+. No WRITE_SETTINGS bypass is requested;
media volume uses the normal MODIFY_AUDIO_SETTINGS permission. Alarm/ringer
streams, mute restrictions and aggregate maximum-lock policy are preserved.
Immediate readback gates applied outcomes; exceptions expose only a fixed safe
message, and partial failures warn that earlier changes may remain.

The optional config.apply managedDisplay section uses a separate
device.configDisplay capability. Successful documents persist/reapply after boot.
Removing a setting stops reapplication and keeps its last OS value. Unsupported
fields remain explicitly unsupported in per-setting results; an aggregate
protocol revision must not be interpreted as every field being enforced.
Malformed documents are rejected before unrelated policies or delivery state
change. No native AGENT_APPLIED_FIELDS, release APK, version or checksum changed.
Release signing, native translation and real test-device acceptance are pending.

## Fresh location (source only)

Adds device.fetchLocation without a last-known fallback. Current owner/admin,
API30, OS permission/service and non-off privacy mode gate both advertisement
and collection. A mode/authority change during capture discards the fix. The
response preserves actual capture time and rejects stale/future/invalid/mock
fixes. Cancellation propagates and failure diagnostics contain no coordinates.
No native command mapping, release signing, location permission grant or real
hardware invocation occurred. Platform retention and per-tenant location scope
must be enforced during later signed integration.

## Launcher appearance (source only)

Adds an exact-capability guarded appearance patch for an already active managed
launcher. It preserves allowed apps, exit credentials, features, lock-task/HOME
claims and volume settings. Colors/icon sizes are validated; bounded header text
is rendered as plain text. Null restores the legacy default and empty hides the
header. The store persists changes; appearance-only redraw skips lock-task/pinned
app entry. This source does not evaluate templates, load background URLs or claim
hardware acceptance. Native metadata translation and release signing remain.
