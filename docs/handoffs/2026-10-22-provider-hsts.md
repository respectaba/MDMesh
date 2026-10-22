# Provider HTTPS persistence

Add a one-year HSTS response header only when Caddy receives HTTPS. No preload
or includeSubDomains is enabled. Plain HTTP deployment shapes remain unchanged;
TLS terminated at another proxy requires header policy on that TLS endpoint.
The existing mdmesh.eziaba.com endpoint already has working native HTTPS.

Before rollout, validate with the running Caddy binary and compare adapted
configuration with only the header handler removed. Patch the live Caddyfile in
place rather than replacing it from this repository: the live configuration has
an additional /ezi/commercial/sync bridge that must remain. Retain original bytes
for rollback and reload only Caddy; no provider, database, installer, credentials,
compose service or device command is changed. Verify HTTPS homepage and public
readiness headers, status and body after reload.
