---
# Allowed version bumps: patch, minor, major
content-governance: patch
---

Jahia no longer indexes the audit history. The history uses its own node type, `ccs:governanceAudit`, and the module deletes the audit history that a previous version stored when it starts.
