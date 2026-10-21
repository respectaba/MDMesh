# On-demand installed APK evidence

`apps.evidence` accepts one to five distinct package names. It is separate from
`apps.scan`, check-in telemetry and periodic inventory; no installed-app census
or OSV request is added. The Device Owner app-management capability is
`app.installationEvidence`. The handler requires that exact token and checks
current Device Owner/admin authority before reading, during hashing and before
returning. Authority loss discards all observations. A request without explicit
package names fails; the platform package `android` is also a valid named target.

For a visible, monolithic package signed by one current signer, the handler
reports the installed version code, SHA-256 of the exact base APK, SHA-256 of the
current DER signing certificate, and observation time. PackageManager supplies
paths and signing information; a request can never supply a file path or URL.
Internal paths and raw exceptions do not enter results.

Package metadata is read again after hashing, followed by another deadline
and authority check. An installation change discards the digest. Split APKs, multiple signers, unavailable packages/reads, zero-byte
artifacts and expired/oversized reads return explicit unknown results without a
partial hash. Limits are 128 MiB per artifact, 256 MiB across a request, and a
10-second monotonic budget checked between reads. Local filesystem/PackageManager
calls are not claimed to have a hard kernel-level deadline.

For a rotated single-signer package, only the current signer (last entry of
Android's documented signing history) is used. Past signers are not substituted
as current identity. Packages requiring split-artifact-set or multi-signer-set
matching need a future full-set manifest format and remain unknown here.

This is installation identity, not a component inventory or vulnerability
verdict. The platform must still bind a tenant-trusted signed publisher manifest
to the package/version/APK/current signer, validate observation freshness, and
match declared components against OSV with consent. Android package IDs are not
Maven coordinates. No claim of device attestation is made.

Qualification: 235 JVM tests (175 core, 19 protocol, 41 policy), debug APK assembly
and core Android lint pass. No real device was commanded; no hardware acceptance,
production installer, checksum/native enforced-label change, signed release or
rollout is included in this PR. The existing fresh-enrollment prerelease remains
unchanged.

Android API references checked October 20, 2026:
- https://developer.android.com/reference/android/content/pm/SigningInfo
- https://developer.android.com/reference/android/content/pm/PackageInfo

The platform additionally requires a fresh `security.observedAt` source timestamp
in the same security report as Device Owner status. New collection stamps it at
collection time; absent/old ownership reports are unavailable even when an
operational heartbeat is fresh. This is reported availability, not attestation.

## Self-package qualification report
Security telemetry optionally includes agentInstallation for com.mdmesh.agent
only, using the same bounded APK/version/signer collector and live Device Owner
checks. Unknown/missing self identity cannot qualify a release. The fresh security
snapshot and self evidence times must both be checked by consumers. This is
reported identity, not hardware-backed attestation or an installed-package census.
Protocol1.6 adds the optional field; old frames continue decoding with null.
New APK signing/qualification is required before deployment; code308 lacks this
report and must not be treated as an exact qualified release.
