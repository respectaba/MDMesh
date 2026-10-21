# Protocol versioning

`protocolVersion` is `MAJOR.MINOR` (semver-ish).

- **MINOR bump** = additive, backward-compatible: new optional fields, new capability keys,
  new command types, new enum members in open registries. Old peers ignore what they don't
  know. No coordinated deploy needed.
- **MAJOR bump** = breaking: removing/renaming a required field, changing a field's meaning,
  removing a command type. Requires a migration window where the server supports both majors.

## Compatibility rules (enforced, not aspirational)

1. **Device ignores unknown command `type`** → replies `status: "unsupported"`. Never crashes.
2. **Server ignores unknown fields** in capability matrices and results.
3. **Server gates on capability**: never sends a command whose `requiresCapability` is missing
   from the device's latest matrix. A device that can't do something simply never advertises it.
4. **Both sides tolerate a peer one MAJOR behind** during migration windows.

## Why this shape

This is the concrete form of the "evolvable / easy-to-migrate" requirement. Because Android
forces a permanent version treadmill (each OS release removes/limits an API), the agent's set
of real capabilities changes over time and across the fleet. Encoding capabilities in the
protocol — rather than assuming a fixed feature set — means a 3-year-old agent on Android 9
and a fresh agent on Android 16 talk to the same server without special-casing.

## History

| version | release | notes |
|---------|---------|-------|
| 1.1 | v0.3.0 | config.apply command + device.configApply capability (additive) |
| 1.2 | v0.3.2 | config.apply `factoryReset` policy key + `factoryReset` policy capability (additive) |
| 1.3 | v0.3.3 | config.apply `safeBoot` policy key + `safeBoot` policy capability (additive; older agents report the key `unsupported`); check-in response `installIntegrity` and `resultsAccepted` (additive; absent `resultsAccepted` = all results taken) |
| 1.4 | v0.3.4 | config.apply / `device.locationMode` mode `off` + `locationOff` device capability (additive; older agents treat `off` as passive, so the server also drops locations reported for a "Do not track" configuration); config.apply `wifiRadio` policy key + `wifiRadio` policy capability (additive; older agents report it `unsupported`; `wifi` keeps meaning "lock Wi-Fi settings"); kiosk `features.lockVolume` (additive; older agents ignore it until their update re-sends config.apply); config.apply result outcome `deferred: <reason>` (non-blocking, like `unsupported`) |

| 1.5 | Ezi agent candidate | On-demand `apps.evidence` / `app.installationEvidence` and optional `security.observedAt` source timestamp in the same ownership report (additive). Older undated ownership remains unknown for installation-evidence availability; new fields are ignored by older peers. |

| 1.6 | Ezi agent candidate | Optional `security.agentInstallation` reports only the management package’s bounded version/APK/signer identity with its source timestamp. Absent or unknown identity cannot qualify an exact release; older peers ignore the new field and older frames decode it as null. Additive, not hardware-backed attestation. |

Protocol version is explicitly encoded in every versioned frame, even when it
 equals the constructor default. Other optional/default fields remain lean. This
 corrects earlier agents that omitted the version under encodeDefaults=false.
