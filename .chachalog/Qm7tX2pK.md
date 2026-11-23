---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Changed Page quality and GEO readiness to report a page with the same rules as the server and AI agents.

The SEO, Readability, Links and Jahia health tabs now show the findings that an AI agent gets for the page, so the two always agree. GEO readiness judges the title, headings, meta description, canonical address, language, structured data and image alt texts with these rules too. A script that reads the GraphQL field `checkPage` now reads its findings under `checkPage { findings { ruleId severity text } }`. The rule `health.graphqlUnavailable` is removed: if an administrator changed it in the system site, it stays there as retired, and can be deleted.
