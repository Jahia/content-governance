# Site settings guide

For site administrators: what **Administration > Sites > (site) > Content governance** changes, and
for whom. The entry needs the `site-admin` permission on the site, and the module enabled on the
site (**Administration > Server > Modules and Extensions > Modules**, **Usage in sites**). Without
either, the screen says so and changes nothing.

The screen has three tabs: **Features**, **AI provider** and **BotRank**.

## Features

One card per section of the drawer (**Page quality**, **GEO readiness**, **Compliance**), each with
a switch for the section and, under it, one switch per tab with a line saying what the tab is for.
Everything is on until you switch it off. Changes apply when you click **Save**; editors see them the
next time they open the drawer.

| You switch off | Editors of the site | The server |
|---|---|---|
| A section | No longer see that section in the drawer. Its dashboard stays listed under **Additional** and says it is switched off for this site. | Refuses the section's calls (GEO checks, compliance scans, AI tasks of that section) |
| A tab | No longer see that tab in the drawer | Refuses the calls of that tab |
| Every section | No longer see the **Content governance** button | Refuses every call |

A tab keeps its own switch while its section is off, so switching the section back on brings back the
tabs as they were.

Two tabs also depend on something outside the site, and say so with a chip next to their switch:

- **AI review** (Page quality) shows **Needs an AI provider** while no model is configured on the
  server. It stays greyed out in the drawer whatever its switch says. The AI assists inside the
  **Accessibility**, **SEO** and **Readability** tabs follow their tab's switch and are greyed out
  the same way.
- **AI citations** (GEO readiness) shows **Needs a BotRank connection**. It stays greyed out in the
  drawer until the site is connected in the **BotRank** tab.

What each switch is stored as: the ids switched off, in the `disabledFeatures` property of the
site's `content-governance` node (`/sites/<key>/content-governance`, never published). Section ids are
`audit`, `geo` and `compliance`; tab ids are `<section>.<tab>`, for example `geo.citations` or
`audit.vitals`. A script sets them with the `saveSiteSettings` mutation (see the root `README.md`).

## AI provider

Read only. It shows whether the model shared by every AI feature is configured, and if so the
provider, the model and whether it reads images (for the alt text assist). A server administrator
sets it in `digital-factory-data/karaf/etc/org.jahia.community.contentgovernance.llm.cfg`; the root
`README.md` lists the keys. The key is never shown, not even here.

Until provider, model and key are all set, every AI feature is greyed out for editors and nothing
leaves the server. The model serves every site of the instance; a site that must not use it switches
off the **AI review** tab, and the tabs whose assists it does not want.

## BotRank

Connects the site to a BotRank project, so that the drawer's **AI citations** tab and the
dashboard's citations panels show how AI answers cite the site's pages.

1. A server administrator first adds one file per BotRank organisation in
   `digital-factory-data/karaf/etc/`, named `org.jahia.se.modules.georeadiness.botrank-<id>.cfg`
   (format in the root `README.md`). A connection restricted to other sites is not listed here.
2. Pick the connection, then the project. Save.
3. Optionally, map cited addresses that do not resolve to a page by themselves, and change the time of
   the daily sync.

Editors never call BotRank. The daily sync, on the processing server, stores the citations of the
site's pages; the drawer and the dashboard read what it stored. The details (what the sync reads,
how cited addresses are matched to pages, its limits) are in
[`geo-readiness/dashboard-guide.md`](geo-readiness/dashboard-guide.md) and
[`geo-readiness/architecture.md`](geo-readiness/architecture.md).

After an upgrade from GEO Readiness, the daily sync is off and this tab says so: the module switches
off, the first time it starts on a site's existing data, the syncs and scan schedules it finds. Check
the settings and save them to turn the sync back on; the GEO scan schedule is saved again from the
dashboard the same way.

Binding a site spends the organisation's key, which is why only a site administrator can do it, and
why a server administrator should list the sites of each connection (`sites`) on a shared instance.
If the administrator who saved the binding loses `site-admin`, the sync stops at its next run and this
tab says why; saving the settings again restarts it.
