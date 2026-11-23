---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Added a check type to each content rule, so that its form in jContent shows only the fields of its check.

A rule now has a **Check type**, and the form shows the fields of that type only:

- **Pattern**, for a writing rule: the term, its forms and its replacement.
- **AI**, for a rule that a model checks: the passing score.
- **Code**, for a measure or a check of a module: the unit and the limits.

The field **Evaluator** is now labelled **Check**. Your rules get their check type from their check when the module starts, and keep their values. A rule file that your own module ships must state `checkType: PATTERN`, `AI` or `CODE`, and set only the fields of that type. A module that writes rule nodes through the API must add the mixin of the check type before it sets a field of that type. Example: `ccsmix:patternCheck` before `pattern`.
