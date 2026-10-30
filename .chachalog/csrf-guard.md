---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

The module's servlets go through Jahia's CSRF guard, and the drawer, dashboards and settings send its token. They answer browser sessions only: calls with an API token are no longer accepted.
