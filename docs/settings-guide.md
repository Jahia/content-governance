# Site settings guide

For site administrators: what **Administration > Sites > (site) > Content governance** changes, and
for whom. The entry needs the `site-admin` permission on the site, and the module enabled on the
site (**Administration > Server > Modules and Extensions > Modules**, **Usage in sites**). Without
either, the screen says so and changes nothing.

The screen has two tabs: **Features** and **BotRank**.

## Features

The screen shows one card per panel of the drawer, each with a switch. A panel is a whole screen of
the drawer with its own button in the header of jContent. The **Content governance** panel is first.
A panel that another module adds, such as the chat of the AI assistant, has its card too.

Under the **Content governance** panel, the screen shows one card per section of that panel
(**Page quality**, **GEO readiness**, **Compliance**). Each card has a switch for the section and,
under it, one switch per tab with a line saying what the tab is for. A section that another module
adds to the drawer, such as the AI review of the AI assistant, has its card too, after the sections
of Content Governance.
Everything is on until you switch it off. Changes apply when you click **Save**; editors see them the
next time they open the drawer.

| You switch off | Editors of the site | The server |
|---|---|---|
| The **Content governance** panel | No longer see the **Content governance** button. Every section is off, and its dashboard says so. | Refuses every call of the sections |
| A panel of another module | No longer see the button of that panel, nor the panel in the drawer | Depends on the module of the panel |
| A section | No longer see that section in the drawer. Its dashboard stays listed under **Additional** and says it is switched off for this site. | Refuses the section's calls (GEO checks, compliance scans) |
| A tab | No longer see that tab in the drawer | Refuses the calls of that tab |
| Every section | No longer see the **Content governance** button | Refuses every call |

A tab keeps its own switch while its section is off, so switching the section back on brings back the
tabs as they were. A section keeps its own switch while the **Content governance** panel is off, in
the same way.

**AI citations** (GEO readiness) also depends on something outside the site. Its switch shows the
chip **Needs a BotRank connection**, and the tab stays greyed out in the drawer until the site is
connected in the **BotRank** tab.

What each switch is stored as: the ids switched off, in the `disabledFeatures` property of the
site's `content-governance` node (`/sites/<key>/content-governance`, never published). Panel ids are
`governance`, or the key that another module gives its panel, such as `chat`. Section ids are
`audit`, `geo` and `compliance`, or the key that another module gives its section. Tab ids are
`<section>.<tab>`, for example `geo.citations` or `audit.vitals`. A script sets them with the
`saveSiteSettings` mutation (see the root `README.md`).

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
