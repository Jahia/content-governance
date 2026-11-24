---
# Allowed version bumps: patch, minor, major
content-governance: major
---

The drawer follows the layout of the site. Its main tabs are the domains of the site, such as Page quality, SEO & GEO and Compliance, in the order of the site, and each domain shows its subdomains as sections, in the order of the site. A domain, a subdomain or a rule that the site switches off is not in the drawer. A finding shows in the section of the subdomain of its rule: a site that moves `seo.canonical` to `links` sees its finding in the Links section.

The shipped layout moves SEO to **SEO & GEO**, and Readability and Writing to **Compliance**. The page check, the GEO check and the scan of the terms each run once for the drawer, so a section keeps its results in any tab. A subdomain with no view of its own, such as **Content** of SEO & GEO or **Writing**, lists the findings of its rules. The labels and the help of the shipped domains and subdomains now exist in German and Spanish too.

Content governance no longer registers the drawer tabs `audit`, `geo` and `compliance`, so the site settings no longer switch them. `openPanel('governance', {section})` takes the id of a domain or of a subdomain, such as `{section: 'terms'}`, and the field `section` of a finding names its domain.
