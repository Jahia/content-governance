---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Added the screen **Rules & domains** in jContent > Additional, for site administrators. It orders the domains, the subdomains and the rules of the drawer, moves a rule to another subdomain, and switches the panels, the domains, the subdomains and the rules on or off. Drag an item by its handle, or use its **Move to** menu from the keyboard. **Reset to defaults** removes the layout of the site. A rule that a module ships after the last save shows the mark **New since your last save**.

Server administrators get the same screen for the defaults of every site in Administration > Server > Configuration > **Content governance rules**. A domain and a subdomain now have a default switch, **On**, which this screen writes in the tree of `systemsite`.

The switches left Administration > Sites > Content Governance, which keeps only the BotRank connection. The layout of a site now holds them in its `off` list. When the module starts, the switches that a site saved in an earlier version move into that list, with the ids of the domains and the subdomains, such as `quality` for `audit` and `geo.crawlers` for `geo.crawler`. The GraphQL mutation `saveSiteSettings` is gone: use `saveSiteLayout`. The field `siteSettings.disabled` now lists the ids of the layout that are off on the site.
