---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Added the domains and subdomains of the drawer as content, and the sites that each rule applies to.

Page quality, SEO & GEO and Compliance are the domains, and each tab is a subdomain, such as SEO under SEO & GEO. An administrator changes them in jContent, under the system site. A domain, a subdomain or a rule is on for all sites until an administrator narrows its Sites. A rule file that a module ships names its subdomain, such as `subdomain: seo`, in place of `domain`, or the module skips it. When it starts, the module moves the domain of each rule of your sites to its subdomain. The GraphQL field `contentRules` gives `subdomain` and `sites` in place of `domain`.
