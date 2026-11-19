---
# Allowed version bumps: patch, minor, major
content-governance: major
---

A tab that another module adds to the drawer now gets a switch in the site settings, its serious findings in "Fix first" and a history of its findings. The GraphQL field `sections` of the site settings is removed: a tab is on unless `disabled` names it. The summary of a stored audit gives its counts in `counts { tab count }` instead of one field per tab.
