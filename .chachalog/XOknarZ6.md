---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Changed the page check to give the result of each rule, passed or failed, with the values that its check measured.

The GraphQL field `checkPage` now answers `results { ruleId passed values findings }`, and its fields `findings` and `facts` are removed: a script reads the findings inside each result. Three new rules always pass and give what the tabs show beside the findings: `seo.tags` (the tag list and the sharing preview), `readability.statistics` (the word count, the reading time and the score) and `readability.headings` (the outline of the headings). A site that turns one of them off hides that part of its tab. A site that turns off `links.broken`, `health.unpublishedChanges` or `health.missingTranslations` also hides the links or the state of the content that the rule gives. A check that another module adds can now give values with its result.
