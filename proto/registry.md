# Capability & command registries (v1)

Open string registries. Adding a row is an additive (MINOR) change. Keep this file in sync
with the agent's capability advertisement and the server's command catalog.

## Capability keys

### policy
| key | meaning | min SDK | notes |
|-----|---------|---------|-------|
| `wifi` | toggle/lock Wi-Fi | 24 | |
| `bluetooth` | toggle/lock Bluetooth | 24 | BLUETOOTH_SCAN auto-grant fails if target app targetSdk<=30 |
| `gps` | location toggle | 24 | |
| `mobileData` | mobile data toggle | 24 | |
| `usbStorage` | block USB mass storage | 24 | |
| `camera` | disable camera | 24 | |
| `screenshots` | disable screenshots | 24 | |
| `kioskLockTask` | COSU lock-task kiosk | 24 | setLockTaskPackages/Features |
| `passwordComplexity` | password policy | 31 | reserved complexity API; not advertised by the legacy-quality handler |
| `systemUpdatePolicy` | OS update policy / device-local window | 24 | Device Owner only |
| `passwordRequirements` | password quality and minimum length | 24 | Device Owner + secure lock screen; requirements do not prove current credential compliance |
| `factoryResetProtection` | FRP policy | 30 | setFactoryResetProtectionPolicy |

### appManagement
| key | meaning | notes |
|-----|---------|-------|
| `silentInstall` | PackageInstaller silent install | Device Owner only |
| `silentUninstall` | silent uninstall | Device Owner only |
| `splitApk` | split/.xapk install | |
| `fdroidCatalog` | can pull from an F-Droid repo | for the APK browser/catalog feature |

### remoteControl
Advertised as an object (`tier`, `screenCapture`, `inputInjection`, `transport`). See README.

### oem
`vendor`, `knox` (parked tier).

## Command types

| type | requiresCapability | payload (sketch) |
|------|--------------------|------------------|
| `config.sync` | — | none (triggers a full reconcile) |
| `policy.apply` | the specific policy key | `{ policy: "wifi", value: false }` |
| `policy.systemUpdatePolicy` | `policy.systemUpdatePolicy` | `{ mode: 0..3, from?: "HH:MM", to?: "HH:MM" }`; times required only for mode 2 |
| `policy.passwordRequirements` | `policy.passwordRequirements` | `{ quality: "NONE"|"NUMERIC"|"NUMERIC_COMPLEX"|"ALPHANUMERIC"|"COMPLEX", minimumLength: 0..16 }`; no wipe threshold or passcode |
| `app.install` | `silentInstall` | `{ url, packageName, versionCode, sha256, runAfterInstall }` |
| `app.uninstall` | `silentUninstall` | `{ packageName }` |
| `app.launch` | — | `{ packageName, activity? }` |
| `remote.startSession` | `remoteControl.tier>=view` | `{ sessionId, signaling: {...}, mode: "view"|"control" }` |
| `remote.stopSession` | — | `{ sessionId }` |
| `device.reboot` | — | none (DO) |
| `device.lock` | — | none |

Per-type payload JSON Schemas go in `proto/payloads/` as the registry grows.
