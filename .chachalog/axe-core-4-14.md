---
# Allowed version bumps: patch, minor, major
content-governance: patch
---

Page quality runs axe-core 4.14 (4.14.0 was already bundled in 1.3.0; it is now the declared minimum). The Accessibility tab now also checks that the visible text of a link or button is part of its accessible name (`label-content-name-mismatch`, on by default since 4.14) and reports a few more cases in existing rules, while some false positives are gone (colour contrast, `target-size` on overlapping links to the same page, captions in tables). A page can therefore show a different count than before without its markup having changed. Audits run faster on large pages. The Cypress suite checks that the audit runs the declared axe-core version and that its findings, the new rule included, reach the Accessibility tab.
