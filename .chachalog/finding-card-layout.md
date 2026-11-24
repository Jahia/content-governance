---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Simplified the drawer's finding cards and banners: an icon and the border colour show the severity, and only short titles are bold.

A finding card no longer prints its severity as a word. An icon opens its title line, with a shape per severity, such as a triangle for Moderate, and the colour of its left border matches. Hover the icon to read the severity, and a screen reader reads it too. The **⋮** of its actions ends the title line. The id of its rule sits at the right of the line of its findings, such as `og:image` with `seo.openGraph`. A banner that explains something, such as the note on automated accessibility checks, shows its text in regular weight.
