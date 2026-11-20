---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Showed in the drawer the findings of the page check service, the ones that `frame.check` gives: the tabs SEO, Readability, Links and Jahia health read `checkPage`, and the analyzers of the browser for them are gone. The GEO checks of the title, the H1, the meta description, the canonical, the language, the structured data and the alt texts take their verdict from the same service. `checkPage` now returns an object with `findings` and `facts`, and the rule `health.graphqlUnavailable` is removed.
