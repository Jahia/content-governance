---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

A check that applies its rules together can now give each rule values, such as a score. Such a rule can then show as facts or scores. The new method is `RuleSetCheck.results`, and the package `org.jahia.community.contentgovernance.check.spi` moves to version 1.2.0.

The rule `readability.longSentences` now ends a sentence at the end of a paragraph, a list item, a table cell or a line break. It also leaves out a menu that has the role navigation, menu, menubar, banner or contentinfo. A menu of links no longer counts as one long sentence.

The card of a finding no longer adds the text that a check proposes to its description. That text shows under its occurrence only. The button **Run** of the rules on demand now shows the icon of **Re-run**.
