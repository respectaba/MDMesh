# On-demand installed APK evidence

`apps.evidence` accepts one to five distinct package names. It is separate from
`apps.scan`, check-in telemetry and periodic inventory; no installed-app census
or OSV request is added. The Device Owner app-management capability is
`app.installationEvidence`. A request without explicit package names fails.

For a visible, monolithic package signed by one current signer, the handler
reports the installed version code, SHA-256 of the exact base APK, SHA-256 of the
current DER signing certificate, and observation time. PackageManager supplies
paths and signing information; a request can never supply a file path or URL.
Internal paths and raw exceptions do not enter results.

Package metadata is read again after hashing. An installation change discards
the digest. Split APKs, multiple signers, unavailable packages/reads, zero-byte
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

Qualification: 229 JVM tests (169 core, 19 protocol, 41 policy), debug APK assembly
and core Android lint pass. No real device was commanded; no hardware acceptance,
production installer, checksum/native enforced-label change, signed release or
rollout is included in this PR. The existing fresh-enrollment prerelease remains
unchanged.

Android API references checked October 20, 2026:
- https://developer.android.com/reference/android/content/pm/SigningInfo
- https://developer.android.com/reference/android/content/pm/PackageInfo
