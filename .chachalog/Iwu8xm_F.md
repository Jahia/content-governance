---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Another module can add a content check: it registers a `ContentCheck` service under the id that the evaluator of its rule names. A rule whose check no module registers shows as inactive in `contentRules`, with the missing check. The 16 rules of the HTML check are now rules of the catalog, named `html.*`, and a site can switch each one off.
