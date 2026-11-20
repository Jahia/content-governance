---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Fixed a term rule that matched inside a longer name, such as "Baggins" inside "Sackville-Baggins".

When two term rules match at the same place, the longer match wins, and the shorter rule reports nothing there. With the rules "Baggins" to "Sacquet" and "Sackville-Baggins" to "Sacquet-de-Besace", the text "Lobelia Sackville-Baggins" gets one warning, with the replacement "Sacquet-de-Besace".
