# Site settings guide

For site administrators and server administrators: what the screens of Content Governance change, and
for whom.

- **jContent > Additional > Rules & domains** arranges the drawer of one site. It needs the
  `site-admin` permission on the site, and the module enabled on the site (**Administration > Server >
  Modules and Extensions > Modules**, **Usage in sites**). Without either, the screen says so and
  changes nothing.
- **Administration > Server > Configuration > Content governance rules** sets the defaults of every
  site. It needs a server administrator.
- **Administration > Sites > (site) > Content governance** connects the site to BotRank.

## Rules & domains

The drawer shows its findings in a hierarchy: panel › domain › subdomain › rule. A domain is a main tab
of the drawer, such as **Page quality**, and a subdomain is one of its tabs, such as **Links**.

The screen shows:

- One card **Drawer panels**, with a switch per panel. A panel is a whole screen of the drawer with its
  own button in the header of jContent. The **Content governance** panel is first. A panel that another
  module adds, such as the chat of AI Assistant, has its switch too.
- One card per domain, with a row per subdomain. The button **N rules** of a subdomain opens its rules.
- One card **Tabs of other modules**, when another module adds a tab to the governance panel, such as
  the review of AI Assistant. Each tab has a switch, and a switch per part.

Each domain, subdomain and rule has a switch, a drag handle and a menu **Move to**:

- Drag an item by its handle to change its place. Drop a subdomain on another domain, or a rule on
  another subdomain, to move it there.
- The menu **Move to** does the same from the keyboard: **Up**, **Down**, or into another domain or
  subdomain.

Changes apply when you click **Save**. Editors see them the next time they open the drawer. **Reset to
defaults** removes the layout of the site, which then follows the defaults again.

| You switch off | Editors of the site | The server |
|---|---|---|
| The **Content governance** panel | No longer see the **Content governance** button. Every domain of the panel is off, and its dashboard says so. | Refuses every call of the domains of the panel |
| A panel of another module | No longer see the button of that panel, nor the panel in the drawer | Depends on the module of the panel |
| A domain | No longer see that domain in the drawer. Its dashboard stays listed under **Additional** and says it is switched off for this site. | Refuses the calls of the domain, such as GEO checks and compliance scans |
| A subdomain | No longer see that subdomain in the drawer | Refuses the calls of the subdomain, and its rules do not run |
| A rule | No longer see its findings | The checks do not run the rule |

An item keeps its own switch while its parent is off, so switching the parent back on brings back the
items as they were. Example: you switch off **SEO & GEO**. The subdomain **SEO** keeps its switch on,
and comes back with its domain.

A rule that a module ships after your last save shows at its default place, switched on, with the mark
**New since your last save**. Save to keep it there.

**AI citations** also depends on something outside the site. Its row shows the chip **Needs a BotRank
connection**, and the tab stays greyed out in the drawer until the site is connected to BotRank.

The screen saves the layout of the site, as the catalog of the rules states it:
[`content-rules/catalog.md`](content-rules/catalog.md#the-layout-of-a-site). A script saves it with the
GraphQL mutation `saveSiteLayout`, and removes it with `resetSiteLayout`.

## The defaults of every site

**Content governance rules**, in the server settings, is the same screen for the defaults: the order and
the default switch of each domain, subdomain and rule. It has no panel switches and no reset. A save
reorders, moves and switches the nodes of the tree in `systemsite`, and every site that does not place
an item follows it. Example: you switch off **Ecodesign** there. Every site that did not save its own
layout loses the subdomain **Ecodesign**.

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

After an upgrade from GEO Readiness, the daily sync is off and the screen says so: the module switches
off, the first time it starts on a site's existing data, the syncs and scan schedules it finds. Check
the settings and save them to turn the sync back on; the GEO scan schedule is saved again from the
dashboard the same way.

Binding a site spends the organisation's key, which is why only a site administrator can do it, and
why a server administrator should list the sites of each connection (`sites`) on a shared instance.
If the administrator who saved the binding loses `site-admin`, the sync stops at its next run and the
screen says why; saving the settings again restarts it.
