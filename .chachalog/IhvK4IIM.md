---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Checked the Compliance terms and the glossary of the editorial frame as content rules of one model, so that the Compliance section and frame.check report the same terms.

Each team keeps its terms in a folder of rules with the permissions of that folder, so the legal team keeps terms that the brand team cannot change. Each finding names its rule id and its folder. A glossary term to translate is a replace rule of the language of the translation: "access token" becomes "jeton d'accès" in French. When the module starts, it converts the Compliance glossaries and the glossary terms of the frame into rules in place, with the same identifiers and permissions. The conversion is one-way. A term now ignores accents in every rule, and a star at its end or its start replaces the match modes of the Compliance terms.
