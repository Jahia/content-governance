---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Stored the rules, domains and subdomains of all sites once, in a tree of the system site, and let each site arrange this tree with a layout of its own.

In the system site, under `contents/content-governance`, each domain holds its subdomains and each subdomain holds its rules, in the order of the tabs. An administrator changes a rule there for every site. A site saves its layout with the GraphQL mutation `saveSiteLayout`: the order and the place of its domains, subdomains and rules, and what it switches off. A rule that a module adds later shows on every site, in its subdomain. A rule now belongs to one subdomain: a rule file names `subdomain: seo`, and the GraphQL field `contentRules` gives `subdomain`. When it starts, the module removes the copies of the shipped rules from your sites, and the log names the values that a site had changed.
