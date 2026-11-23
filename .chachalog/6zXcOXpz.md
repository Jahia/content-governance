---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Changed each Page quality finding to take its text from its rule, in the editor's language, and its tab from its domain.

The alt text, language and heading findings now show in Accessibility, and the Web Vitals findings in Ecodesign. Each axe-core check is a rule that a site can switch off in its layout, and that an administrator can give another severity in the system site. Readability has two rules, `readability.flesch` and `readability.lix`, each with its own limit; they replace `readability.hardText` and `readability.veryHardText`. A rule file that a module ships must state its subdomain, such as `seo`, or the module skips it.
