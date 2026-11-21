---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Upgraded the accessibility engine to axe-core 4.14.1. It mostly removes false positives in the Accessibility tab, such as on empty table headers, links in a text block and target size, and it now reports invalid zoom settings of a page (`user-scalable`, `maximum-scale`). A page can therefore show a different count than before without its markup having changed.
