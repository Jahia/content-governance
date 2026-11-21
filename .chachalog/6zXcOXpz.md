---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Changed each Page quality finding to take its text from its rule, in the editor's language, and its tab from its domain.

The alt text, language and heading findings now show in Accessibility, and the Web Vitals findings in Ecodesign. Each axe-core check is a rule that a site can switch off or give another severity. Readability has two rules, `readability.flesch` and `readability.lix`, each with its own limit; they replace `readability.hardText` and `readability.veryHardText`. A rule file that a module ships must state its domain, such as `seo`, or the module skips it. If your site changed an `html.*` rule, set its domain to `accessibility`, or it stops running.
