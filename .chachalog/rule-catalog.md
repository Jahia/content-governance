---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Added a catalog of content rules, which a site changes as content, so that every check applies one value per rule.

Each finding shows the id of its rule, and each rule names what checks it. A site turns a rule off, or changes its severity or its limits, under Content folders > content-insights > content-governance > rules, and adds a rule of its own under Content folders > content-insights > site > rules. Another module ships rules with the manifest header Jahia-Content-Rules. A new version updates the rules that a site did not change. The writing rules of the editorial frame are content rules too. Some defaults changed: a GEO title now passes from 30 to 60 characters, and a GEO meta description must be 50 to 160 characters.
