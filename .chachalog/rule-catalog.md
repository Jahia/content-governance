---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Added a catalog of content rules, which an administrator changes as content, so that every check applies one value per rule.

Each finding shows the id of its rule, and each rule names what checks it. An administrator changes the severity or the limits of a rule for every site, under Content folders > content-governance of the system site. A site adds a rule of its own under its Content folders. Another module ships rules with the manifest header Jahia-Content-Rules. A new version updates the rules that an administrator did not change. The writing rules of the editorial frame are content rules too. Some defaults changed: a GEO title now passes from 30 to 60 characters, and a GEO meta description must be 50 to 160 characters.
