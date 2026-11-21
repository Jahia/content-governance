---
# Allowed version bumps: patch, minor, major
content-governance: patch
---

Fixed the tab Report of GEO readiness, which said "No AI model available" when the server refused or did not answer its status call, such as after a CSRF token mismatch. The tab now says that it could not reach the server and offers a retry, and a CSRF refusal that the guard answers with an error status is sent again with fresh tokens.
