---
# Allowed version bumps: patch, minor, major
content-governance: patch
---

The GEO readiness dashboard no longer risks having a request refused by Jahia's CSRF guard when it loads: requests sent at once to the same module address now wait for the first one's answer instead of all using the session's master token, which the guard accepts once per address. The Cypress suite now runs every night against Jahia 8.2.3.2 in GitHub Actions.
