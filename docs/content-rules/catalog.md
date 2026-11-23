# The catalog of content rules

The checks of Page quality, of GEO readiness and of the editorial frame apply content rules. Each
rule is a content node with one id and one value. Example: the rule `seo.title` accepts a title of 30
to 60 characters. The SEO tab reports a title under this rule, and the GEO score counts it.

The rules that the modules ship live once, in a tree of the system site that every site reads. Each
site arranges this tree with its own layout: the order of the tabs, the place of each rule, and what
the site switches off.

## What a rule holds

A rule is a node of type `ccs:contentRule`, and the module, a site or an import writes it with these
properties. jContent shows a label for each property, such as **Check** for `evaluator`.

| Property | What it means |
|---|---|
| `ruleId` | The name of the rule, such as `seo.title`. Each finding of the rule shows it. One rule of a site has it. |
| `checkType` | The check type of the rule: `PATTERN`, `AI` or `CODE`. jContent labels it **Check type**. It picks the fields of the rule, as the section [The fields of each check type](#the-fields-of-each-check-type) shows. |
| `evaluator` | The check of the rule. jContent labels it **Check**. The table under this one lists the values. |
| `subdomain` | The id of the subdomain of a rule outside the tree, such as `writing`. The findings of the rule show in it. [Domains and subdomains](#domains-and-subdomains) lists the subdomains. A rule of the tree takes the subdomain of its parent node, and has no value here. A rule with no subdomain takes the subdomain of the part of its id before the first dot, when a module declares it. Else a rule of the evaluator `pattern` takes `terms`. A rule whose subdomain nobody declares never runs. |
| `sites` | The keys of the sites where the rule is on by default, or `*` for all sites, the default. jContent shows `*` as "All sites". The layout of a site wins over it. |
| `severity` | `critical`, `serious`, `moderate` or `minor`. |
| `blocking` | A finding of a blocking rule stops the publication of the content. |
| `enabled` | On or off by default. A rule that is off reports nothing, on each site whose layout does not place it: the layout of a site wins over this switch. |
| `fixer` | The role of the person who fixes a finding: `editor`, `developer` or `administrator`. Example: `geo.reachable`, a crawler that gets a 403, names an administrator. |
| `title` | The title of a finding, one text per language, with one line per plural form, such as `one: {{count}} image has no text alternative` and `other: {{count}} images have no text alternative`. It takes the values `{{count}}` (the findings of the rule), `{{value}}` (what the first finding measured or matched), `{{min}}` and `{{max}}`. |
| `label` | A neutral name of what the rule checks, one text per language, such as "Title length". GEO readiness shows it on each check. |
| `advice` | What to do about a finding, one text per language. For a rule of the check type `AI`, the criterion that the model checks. |
| `unit`, `minimum`, `maximum` | The limits of a rule of the check `threshold`, in its unit. For a rule of the check type `AI`, `minimum` is the passing score, from 0 to 100. |
| `kind`, `pattern`, `matchMode`, `replacement`, `ruleLanguage` | The pattern of a rule of the check `pattern`: a forbidden term, a term to replace, a mandatory mention or a style pattern. A term ignores case and accents, a `*` at its end matches the rest of the word, and a `*` at its start lets it end another word. A regular expression ignores case, unless it starts with `(?-i)`. |
| `patternForms`, `replacementForms` | The other forms of the term and of the replacement, such as the plurals "access tokens" and "jetons d'accès". |
| `patternLanguage` | For a glossary term to translate, the language of the term, such as `en` for "access token" in a rule of `fr`. `frame.check` on a translation then reports a missing replacement. |
| `wrongExamples`, `rightExamples`, `owner`, `decidedOn`, `validFrom`, `validUntil` | The examples, the decision and the dates of a writing rule. |
| `runtime` | Where the check of the rule runs: `browser` for a check that the drawer runs on the preview of the page, else `server`. Empty: `server`. The server never runs a rule of the browser, never blocks a publication on it, and shows it as active. |
| `retired` | Set by the module on a rule that an administrator changed and that its module no longer ships. |

| Check (`evaluator`) | Check type | What checks the rule |
|---|---|---|
| `pattern` | `PATTERN` | The pattern of the rule, with no model. `frame.check` reports each match. |
| `threshold` | `CODE` | A measured value against `minimum` and `maximum`. Example: `seo.title` measures the length of the title. |
| `presence` | `CODE` | Something that the rule names is present. |
| `model` | `AI` | A model, against the advice of the rule. Only a module that calls a model registers it, so content-governance alone checks no such rule. |
| Any other value | The type of that check | The id of a check that a module registers. A built-in rule with no limit names a check of its own id, such as `seo.indexable`. |

Each value of `evaluator` is the id of a check that a module registers, as the section
[How a module adds a check](#how-a-module-adds-a-check) shows. Content-governance registers
`pattern`, `threshold`, `presence` and the check of each of its own rules. A rule whose check no
started module registers is inactive and never runs. Example: a rule with the check `model`
shows `active: false`, `missingCheck: "model"` and `needs: "ai-assistant"` in the GraphQL field
`contentRules`.

### The fields of each check type

The check type of a rule picks the fields that the rule holds. Each check type keeps its fields on a
mixin, and the other properties are on every rule:

| Check type | Mixin | Fields | Example |
|---|---|---|---|
| `PATTERN` | `ccsmix:patternCheck` | `kind`, `pattern`, `matchMode`, `replacement`, `ruleLanguage`, `patternForms`, `replacementForms`, `patternLanguage`, `wrongExamples`, `rightExamples`, `owner`, `decidedOn`, `validFrom` and `validUntil` | A term rule of the check `pattern`. |
| `AI` | `ccsmix:aiCheck` | `minimum`, the passing score | A rule of the check `model`, whose advice is the criterion. |
| `CODE` | `ccsmix:codeCheck` | `unit`, `minimum` and `maximum` | `seo.title`, of the check `threshold`. |

In jContent, the form of a rule shows the fields of its check type only. Each mixin is a
`jmix:dynamicFieldset`, and the value of **Check type** adds its mixin to the rule. Example: a site
picks **AI** for the rule `acme.tone`, and the form shows **Passing score** and no pattern.

A module that writes a rule node with the API sets `checkType` and adds the mixin of the check type.
It adds the mixin first, because the repository refuses a field whose mixin the node lacks.

The check type copies the kind of the check that `evaluator` names. Content-governance does not yet
compare the two: a rule of the check type `PATTERN` that names the check `model` gets no warning.

## Where the rules live

```text
/sites/systemsite/contents/content-governance/    the tree, which every site reads
    quality/                                      a domain
        health/                                   a subdomain of the domain
            health.placeholderText                a rule of the subdomain
        ...
    geo/
        seo/
            seo.title                             ruleId, evaluator, sites, severity, minimum, maximum, advice...
/sites/<site>/contents/content-governance/        the layout of the site, in the property layout
    ai-assistant/rules/, ai-assistant/glossary/   the rules that the site adds, with ai-assistant
```

The checks read the rule nodes of the system site and of the site, wherever they are. A rule node of
the tree takes the subdomain of its parent node. A rule node elsewhere names its subdomain in the
property `subdomain`, such as the writing rules that the frame import of ai-assistant writes.

## How a module ships rules

A module names a folder of its bundle in the manifest header `Jahia-Content-Rules`. Each `.md` file
of that folder is one rule. This module ships its rules in `src/main/resources/content-rules`:

```xml
<Jahia-Content-Rules>content-rules</Jahia-Content-Rules>
```

```markdown
---
id: seo.title
evaluator: threshold
checkType: CODE
subdomain: seo
severity: serious
fixer: editor
unit: characters
minimum: 30
maximum: 60
---
Give the page a title, with the main subject first, inside the length range.
```

The frontmatter holds the properties of the node, with `id` for `ruleId`, one id for `subdomain`,
and a list for `sites`. The body is the advice in English. The module skips a file with a warning
in the log when the file has one of these defects:

- An unknown key. The key `domain` and the key `subdomains` of a snapshot before 2.0.0 are unknown
  keys.
- No evaluator, or an unknown severity.
- No check type, or a key of another check type. Example: a file of the check type `CODE` with a
  `pattern`.
- No subdomain, a list of subdomains, or a subdomain that no started module declares.

The texts of a rule take a map of languages. The drawer shows them in the language of the
interface of the editor, and in English when the rule has none in that language:

```markdown
label:
  en: "Text alternatives of images"
  fr: "Alternatives textuelles des images"
title:
  en:
    one: "{{count}} image has no text alternative"
    other: "{{count}} images have no text alternative"
  fr:
    one: "{{count}} image sans alternative textuelle"
    other: "{{count}} images sans alternative textuelle"
advice:
  fr: "Donnez à chaque image un attribut alt : une description, ou une valeur vide pour une image décorative."
```

The module writes only the English texts on the node. An administrator translates a text on the
node, and this translation replaces the one of the file in that language.

The module writes the rules of each module once, in the tree of the system site, under the node of
their subdomain. The seeder runs on the processing server when a module that ships rules starts. A
rule of a subdomain that the tree does not hold yet waits. The seeder writes it when the module that
declares the subdomain starts.

## How an administrator changes a rule

Open the node of the rule in jContent, on the system site, under **Content folders >
content-governance**, and change a property. The change applies to every site. Example: an
administrator sets the minimum of `seo.title` to 40. At the next run, the SEO tab of every site
reports a title of 35 characters under `seo.title`, with the range 40 to 60.

The change applies at the next check, with no publication, because the checks read the edit
workspace. A property that the node lacks keeps the value that the module ships.

To move a rule to another subdomain for every site, move its node under the node of that subdomain.
A node that an administrator deletes comes back with its shipped values when the module starts
again. Delete a node to reset its rule. Turn a rule off to stop it on every site that has no layout,
or whose layout does not name it. A site whose layout places the rule keeps it on, because the layout
of a site wins over the default switch.

A site cannot change the values of a rule. It arranges the rules and switches them on or off, as
[The layout of a site](#the-layout-of-a-site) states. A rule node of the site with the id of a rule of
the tree, or of a rule that a module ships, changes nothing.

## How a site adds a rule

Create a **Content rule** in jContent, under **Content folders > content-governance >
ai-assistant > rules** of the site. Give it a rule id, a check type, a check, a severity, an advice
and a subdomain. For example, the rule `acme.tone` has the check type **AI**, the check `model` and
the severity `minor`. Its subdomain is `writing`, and its advice is "Write in a friendly tone."

The id is free. Name an existing subdomain, such as `writing` for `acme.tone`, so that the findings
of the rule have a tab. A term with no subdomain, such as `brand.seamless`, takes `terms`. A site
cannot add a domain or a subdomain. jContent refuses a rule whose id another rule of the site already
has: "The rule acme.tone already exists at /sites/...". It also refuses the id of a rule of the system
site, such as `seo.title`, because a site does not change the values of a rule.

## How a new version of a module updates the rules

Each node that the module writes keeps a fingerprint of the values it wrote, in the hidden property
`seedDigest`. The fingerprint holds the subdomain of a rule and the domain of a subdomain. A node
whose values and place still match the fingerprint is a node that nobody changed.
[content-governance#79](https://github.com/Jahia/content-governance/issues/79) decided this rule.

| The rule in the new version | Nobody changed the node | An administrator changed or moved the node |
|---|---|---|
| New | The module adds it. | The module adds it. |
| Changed | The node takes the new values. | The node keeps its values, and gets the properties it lacks. |
| Moved to another subdomain | The node takes the new values, and moves under the new subdomain. | The node keeps its values and its place. |
| Moved to the browser | The node takes the new values. | The node keeps its values, gets the properties it lacks, and takes the check of the new version. |
| Gone | The module removes the node. | The node stays, with `retired` set. |

Example: version 2.0 moves `ecodesign.pageWeight` to the browser, with the evaluator
`ecodesign.pageWeight`. An administrator who raised its maximum keeps this maximum, and the
evaluator `threshold`, which no check of the browser has, becomes `ecodesign.pageWeight`.

The domains and the subdomains follow the same rule. A domain or a subdomain that its module no
longer ships stays, with `retired` set, while it holds another node.

A module that stops keeps its nodes in the tree. A check that the module registered goes with it,
so its rules show as inactive until the module starts again.

## The upgrade from an earlier version

Content-governance converts the rules of an earlier version once, when it starts:

- The folder `contents/content-insights/jahia` becomes `contents/content-insights/content-governance`.
  A rule that the site changed keeps its values and gets its evaluator and its advice.
- Each `efnt:writingRule` of the editorial frame becomes a `ccs:contentRule` in place, in the edit
  and the live workspace, with the same identifier. Its id is `writing.<node name>`, its subdomain is
  `writing`, and its evaluator is `pattern`. The severity `warning` becomes `moderate`, and
  `blocking` becomes `serious` with `blocking` set. A rule of the kind `style` is `minor`, so that it
  stays a soft rule. The justification of each language becomes the advice of that language. Every
  other value stays.

Version 2.0.0 gives each rule one subdomain, in the property `subdomain`. A snapshot before 2.0.0
held a domain in the property `domain`, or several subdomains in the property `subdomains`. When
content-governance starts on the processing server, before it writes the rules:

- A rule that the module ships takes the subdomain of its file. Example: `geo.fetched` held
  `domain: geo`, and takes `subdomain: geo.crawlers`. `seo.title` held `subdomains: [seo, geo.score]`,
  and takes `subdomain: seo`.
- Another rule takes its first subdomain, or its domain, and `compliance` becomes `terms`. When no
  module declares that subdomain, such as `geo` for a rule that a site wrote, the rule shows with its
  unknown subdomain. An administrator then picks one of the declared subdomains on the rule node.
- The properties `domain` and `subdomains` go.
- A rule that nobody changed keeps a fingerprint that says so.

Version 2.0.0 also writes the rules once, in the tree of the system site, and no longer in each
site. When content-governance starts on the processing server, after it converts the subdomains:

- It removes the copies of the shipped rules in each site, under
  `contents/content-insights/<module>/rules`. The log names the values of a copy that the site
  changed, because a site no longer changes the values of a rule. A rule that the site wrote there
  stays, and so do the rules of `contents/content-insights/site/rules`.
- It removes the folders `contents/content-insights/<module>/domains` and `subdomains` of the system
  site, and every domain or subdomain node that a site wrote, and the log names the values of each
  node. A domain or a subdomain node that holds other nodes, such as rules, stays, and the log names
  it. The seeder then writes the tree.
- It removes each folder that holds nothing more.

Version 2.0.0 also moves the fields of each check type from `ccs:contentRule` to the mixin of that
type, and adds `checkType`. When content-governance starts on the processing server, before it
converts the subdomains:

- A rule with no `checkType` takes the check type of its check. The check `pattern` or no check gives
  `PATTERN`, `model` gives `AI`, and any other check gives `CODE`. Example: `seo.title`, of the
  check `threshold`, takes `CODE`.
- The rule takes the mixin of its check type, which shows its fields again. A field of another check
  type stays in the repository, out of sight, and the log names it.
- A rule that nobody changed keeps a fingerprint that says so.

No release before 2.0.0 holds the type `ccs:contentRule`, so a 1.x instance needs nothing. An
instance that runs a 2.0.0 snapshot refuses the new definition, which removes the property
`subdomains` of a rule. It also removes the properties `domain` and `order` of a subdomain. On such an instance, install 2.0.0
once with `ignoreChecks: true` on the `installOrUpgradeBundle` entry of the provisioning.

## Domains and subdomains

The drawer shows the findings in a hierarchy: drawer › panel › domain › subdomain › rule. A panel is
a screen of the drawer, such as the governance panel. A domain is a main tab of a panel, such as Page
quality, and a subdomain is a tab of a domain, such as SEO. Each rule belongs to one subdomain.

In the tree of the system site, a domain node holds its subdomain nodes, and a subdomain node holds
its rule nodes. The order of the child nodes is the order of the tabs. Example: an administrator
moves the node of the subdomain `seo` under the domain `quality`. The SEO tab then moves with all its
rules on every site, and no rule changes.

Content-governance ships this layout:

| Domain | Subdomains, in order |
|---|---|
| `quality` Page quality | `health` Jahia health, `links` Links, `accessibility` Accessibility, `vitals` Web Vitals, `ecodesign` Ecodesign |
| `geo` SEO & GEO | `geo.citations` AI citations, `seo` SEO, `geo.score` Score, `geo.content` Content, `geo.files` Site files, `geo.schema` Structured data, `geo.crawlers` Crawler access |
| `compliance` Compliance | `readability` Readability, `terms` Terms, `glossary` Glossary, `writing` Writing |

The GEO score counts rules of other subdomains. The subdomain `geo.score` names them in `counts`, for
a screen that lists them. Example: `seo.title` belongs to `seo`, and the GEO score counts it. The GEO
score keeps its own list in its code, so a change of `counts` does not change the score.

### What a domain and a subdomain hold

A domain is a node of type `ccs:contentDomain`, and a subdomain a node of type
`ccs:contentSubdomain`.

| Property | What it means |
|---|---|
| `domainId`, `subdomainId` | The id, such as `quality` or `seo`. The ids are flat and unique. A module prefixes its own, such as `acme.prices`. The name of the node is the id. |
| `panel` | The panel of a domain: `governance`, the default, or a panel of another module. |
| `sites` | The keys of the sites where it is on by default, or `*` for all sites, the default. The layout of a site wins over it. |
| `label`, `help` | The name of the tab and what it checks, one text per language. |
| `counts` | The ids of the rules of other subdomains that a subdomain counts, such as the rules of the GEO score. |
| `retired` | Set by the module on a node that an administrator changed and that its module no longer ships. |

### How a module ships domains and subdomains

A module ships them beside its rules, under the same manifest header. Each one is a Markdown file in
the subfolder `domains` or `subdomains` of the rule folder. The body is the help in English:

```markdown
---
id: acme.prices
domain: compliance
order: 50
label:
  en: "Prices"
  fr: "Prix"
help:
  fr: "Les prix que l'ERP donne."
---
The prices that the ERP gives.
```

The key `domain` names the domain of a subdomain. The key `order` places a new node among the nodes
that the modules ship: a lower order comes first. The seeder reads `order` only when it adds the
node, and an administrator can move the node afterwards.

A subdomain names a domain, and a rule names a subdomain, that the same module, content-governance or
another started module declares. Another file is skipped with a warning in the log.
When another module starts and declares domains or subdomains, content-governance reads the skipped
files again. Example: a rule of `acme-rules` names the subdomain `acme.prices` of `acme-subdomains`.
The rule applies once `acme-subdomains` starts, even when `acme-rules` started first.

The seeder keeps what an administrator changed or moved, as for the rules.

### The layout of a site

A site arranges the tree with its layout, the JSON of the property `layout` of its folder
`contents/content-governance`:

```json
{
  "domains": [
    {"id": "geo", "subdomains": [{"id": "seo", "rules": ["seo.title", "seo.metaDescription"]}]},
    {"id": "quality", "subdomains": [{"id": "links", "rules": ["links.broken", "seo.canonical"]}]}
  ],
  "off": ["vitals", "seo.indexable"]
}
```

The position in an array is the order, and the parent array is the place. In this example, `digitall`
shows SEO & GEO before Page quality, and reports `seo.canonical` in the Links tab. It switches off
the Web Vitals tab and the rule `seo.indexable`.

- The layout places an item that it names in an array. Such an item is on, unless `off` names it.
- An item that the layout does not name keeps its place in the tree, after the items that the layout
  places. It is on when its `sites` include the site. Example: a new version of a module ships a new
  rule. This rule shows in its subdomain on every site, also on a site that saved its layout before.
- `off` switches off an item, placed or not. A subdomain that is off switches off its rules, and a
  domain that is off switches off its subdomains.
- A site with no layout shows the tree as it is.

The GraphQL mutation `saveSiteLayout(siteKey, layout)` saves the layout of a site, and
`resetSiteLayout(siteKey)` removes it. Both need the permission `site-admin` on the site, and the
system site has no layout. The query `siteLayout(siteKey)` gives the layout that a site saved.

### Which rules apply to a site

A rule runs on a site when all of these are true:

- The layout of the site does not switch it off.
- The layout places it, or its `sites` include the site and it is enabled.
- Its subdomain on the site, and the domain of that subdomain, are on.
- The site installs the module that ships the rule. A rule that the site or `systemsite` adds needs no
  module.

Example: an administrator sets the `sites` of the domain `geo` to `digitall`. The site `luxe` then
has the SEO & GEO tabs off, and their rules, such as `seo.title` and `geo.fetched`, do not run there.
A site whose layout places the domain `geo` keeps it on.

The GraphQL field `contentDomains(siteKey, language, includeOff)` gives the domains of a site in the
order of its layout. Each domain comes with its subdomains, and with its label and its help in the
language. With `includeOff`, the field also gives the domains and the subdomains that are off. The
field `on` tells whether an item is on. The field `isNew` marks an item that the saved layout of the
site names neither in an array nor in `off`, such as an item that a module shipped after the save. A
layout that names fewer items marks the others as new too. The field `contentRules` gives the
`subdomain`, the `order`, `enabled` and `isNew` of each rule on the site. The drawer keeps its tabs
for now: it places a finding in the tab of the subdomain of its rule, when the drawer has one.

## How a module adds a check

A module registers a check as an OSGi service of the interface `ContentCheck`, from the package
`org.jahia.community.contentgovernance.check.spi` that content-governance exports. The property
`check.id` holds the id that the evaluator of a rule names:

```java
@Component(service = ContentCheck.class, property = "check.id=acme.erpPrice")
public class ErpPriceCheck implements ContentCheck {
    public Set<Scope> scopes() { return Set.of(Scope.VALUE); }
    public List<Finding> check(CheckInput input, Rule rule) { ... }
}
```

Content Governance calls `result(input, rule)` of a check, which gives the result of the rule:
`CheckResult(ruleId, passed, values, findings)`. Its default method wraps `check`: the rule passes when
there is no finding, with no value. A check that measures values overrides it to put them in the
result, such as the length and the text of a title:

```java
public CheckResult result(CheckInput input, Rule rule) {
    return CheckResult.of(rule, Map.of("length", 92, "text", "Annual report…"), check(input, rule));
}
```

The module ships the rule of the check with the header `Jahia-Content-Rules`, as the section
[How a module ships rules](#how-a-module-ships-rules) shows, with `evaluator: acme.erpPrice` and
`checkType: CODE`. The test
module of this repository, `tests/jahia-module`, does both, with no change to content-governance.

A check states the scopes in which it runs:

| Scope | What the check reads | Who runs it today |
|---|---|---|
| `VALUE` | One value that an editor writes. | The GraphQL field `checkText` and `frame.check` of the editorial frame. The Compliance scan runs the rules of the subdomains of the domain `compliance` on each text, but `readability`, whose rules measure a whole page. |
| `PAGE` | The HTML of a rendered page or content item. | The page check (`HtmlCheck`): the GraphQL field `checkPage`, which the drawer reads, `frame.check` with a path, the tool `audit.accessibility`, `checkText` with the scope `PAGE`, and the SEO checks of GEO readiness on the HTML that a crawler gets. |
| `SITE` | A whole site. | No caller yet. |

A run applies each rule that the site keeps on and whose check is registered for the scope. It gives
one result per rule, passed or failed. Each finding carries the id of the rule, the severity
that the site gives the rule, and the text that breaks the rule. A check that throws an exception
gives no result, and the other checks still run. One rule gives 50 findings or fewer on one input.

A finding on a value can also carry values, which the Compliance section and `frame.check` read:

| Value | What it holds |
|---|---|
| `start` | The place where the text of the finding starts, in the value as a reader reads it: tags removed, entities decoded, white space collapsed. |
| `end` | The place where the text of the finding ends, excluded. |
| `term` | The term of the rule that the text holds, such as "click here". |
| `replacement` | What to write instead, such as "select". |

Example: `{start: 4, end: 14, term: "click here", replacement: "select"}` in "Now click here to buy".
With no `start`, the Compliance section marks the first place of the text of the finding.

A check whose rules act on each other implements `RuleSetCheck` instead of `ContentCheck`. A run
then calls it once per input, with all its rules, and gives each finding to the rule that it names.
Each of these rules gets a result with no value, which passes when the rule has no finding. The
check `pattern` is one. The term "Sackville-Baggins" of one rule hides the term "Baggins" of
another rule. A glossary rule of another language reports a form of its term.

```graphql
query {
  contentGovernance {
    checkText(path: "/sites/digitall/home", language: "en", text: "The kit costs 12.50 EUR.") {
      ruleId severity text context
    }
  }
}
```

`threshold` and `presence` judge a value that the caller measured. The caller gives it as the fact
of the input whose key is the id of the rule: `input.withFact("seo.title", 72)`. With no such fact,
they find nothing. The values of their result are the ones that the caller gives with
`input.withValues("seo.title", Map.of("length", 72, "text", "…"))`. Without them, `threshold` gives
`value`, the measured number.

A measure rule has no limit: its check finds nothing and gives values only, so the rule always
passes. The drawer shows these values, and a site switches the rule off in its layout to hide them.
Example: `readability.statistics` gives the word count and the reading time of the Readability tab.

The checks of SEO, readability, links and Jahia health run on the server, as checks of the scope
`PAGE`, and the drawer shows their findings.

Some rules need a browser, because they measure what a
browser loads and runs: axe-core, the web vitals and eco-design. Such a rule states `runtime: browser`.
Its check is an entry of the registry of jContent, and the drawer runs it on the preview of the page.
[`drawer-entries.md`](../drawer-entries.md#add-a-check-that-runs-in-the-browser) states how a module
adds one, with no Java code. The server never runs a rule of the browser, so the tools for agents never
report it, and shows it as active. A browser rule never blocks, even when its node sets `blocking`.

## One id for each practice

Version 1.12 keeps one rule id for each practice. A check on the server and a check in the drawer
report one practice under the same id.

| Removed id | Kept id |
|---|---|
| `seo.imageAlt`, `geo.imageAlt` | `html.imageAltMissing` |
| `seo.lang` | `html.pageLanguageMissing` |
| `seo.langMatches` | `html.pageLanguageMismatch` |
| `seo.singleH1` | `html.headingH1Missing` when the page has no h1, `html.headingH1Multiple` when it has more than one |
| `readability.headingSkips` | `html.headingLevelSkipped` |
| `seo.genericAnchors` | `html.linkTextVague` |
| `html.pageTitleMissing` | `seo.title`: a missing title has the length 0 |
| `html.pageDescriptionMissing` | `seo.metaDescription`: a missing description has the length 0 |
| `health.graphqlUnavailable` (2.0) | None: the drawer reads the state of the content from the check of the server, and a check that fails shows as unfinished in its tabs |

The tree of the system site keeps the nodes of the removed ids until content-governance starts
again, and then the table of
[How a new version of a module updates the rules](#how-a-new-version-of-a-module-updates-the-rules)
applies. The module removes a node that nobody changed. A node that an administrator changed stays,
with `retired` set. No module registers its check any more, so its rule shows as inactive and names
its own id as the missing check. `geo.imageAlt` is the exception: its evaluator `threshold` stays
registered, but no caller measures its value, so it gives no finding. To keep a change, such as a
severity, set it again on the node of the kept id.

## How a check reads the rules

`ContentRules` gives the rules of a site in three layers, the most specific last:

1. The rules that the modules of the site ship.
2. The rule nodes of `systemsite`, which apply to every site: the tree, and any other rule node there.
3. The rule nodes of the site whose id no module ships and `systemsite` does not hold.

A node of `systemsite` replaces the shipped rule of its id, and keeps the shipped values that it lacks.
A node of `systemsite` of a rule that a module ships applies only to a site that enables that module. A
node of the site adds a rule of its own, and a node of the site with the id of another rule changes
nothing. The rules then apply as [Which rules apply to a site](#which-rules-apply-to-a-site) states.
Example: a node `brand.noClickHere` in `/sites/systemsite/contents` applies to every site, and a node
`seo.title` of `digitall` changes nothing.

- The Java checks of GEO readiness read the rules of the site through `ContentRules.forSite`. A site
  scan reads them once, at its start.
- The checks of Page quality on the server read them through `ContentRules.forPath`, at the start of
  each check. The drawer reads them through the GraphQL field
  `contentGovernance.contentRules(siteKey)`, at the start of each audit, for the checks of the
  browser, the limits in the texts of a finding, and the advice of a rule that no tab words. Each
  rule gives its `evaluator`, `runtime` and `blocking` there too.
- The SEO assist reads the ranges of `seo.title` and `seo.metaDescription`, and states them in its
  prompt.
- The editorial frame reads the rules of the evaluator `pattern` from the live workspace, and the
  prompt of a model carries them. `frame.check` runs the rules of the edit workspace through the
  check registry, as the Compliance scan does.
- A rule with no node, and a site that cannot be read, use the values that the module ships.

Each finding carries the id of its rule: `rule` on a recommendation of Page quality, and `rule` on a
check of GEO readiness. The site scan of GEO readiness stores the rule of each check in `rules`,
beside `severities`.

GEO readiness shows three severities. `critical` stays critical. `serious` and `moderate` show as
important. `minor` shows as advisory.

## What the catalog does not hold

- The catalog holds no value of a measure rule, such as the tag checklist of SEO. A check measures
  these values on each page.
- The limits of a scan, such as the number of links that the Links tab checks, are not rules.
- The length hints of the social card of the SEO assist (`og:description`) are not rules.
- The instructions that ai-assistant gives a model for an SEO review state the default ranges of
  `seo.title` and `seo.metaDescription`. A change on a site does not reach them.

## The rules of this module

| Rule | Evaluator | Severity | Limits | Fixed by |
|---|---|---|---|---|
| `seo.title` | `threshold` | serious | minimum 30, maximum 60 characters | editor |
| `seo.metaDescription` | `threshold` | serious | minimum 50, maximum 160 characters | editor |
| `seo.indexable` | `seo.indexable` | critical | none | editor |
| `seo.followLinks` | `seo.followLinks` | moderate | none | editor |
| `seo.canonical` | `seo.canonical` | moderate | none | developer |
| `seo.openGraph` | `seo.openGraph` | moderate | none | editor |
| `seo.structuredData` | `seo.structuredData` | moderate | none | developer |
| `seo.structuredDataValid` | `seo.structuredDataValid` | serious | none | developer |
| `seo.tags` | `seo.tags` | minor | none: a measure | none |
| `geo.fetched` | `geo.fetched` | critical | none | administrator |
| `geo.reachable` | `geo.reachable` | critical | none | administrator |
| `geo.policyMatchesReality` | `geo.policyMatchesReality` | serious | none | administrator |
| `geo.noClientRedirect` | `geo.noClientRedirect` | critical | none | developer |
| `geo.namedInRobots` | `geo.namedInRobots` | minor | none | administrator |
| `geo.guestReadable` | `geo.guestReadable` | critical | none | administrator |
| `geo.contentInInitialHtml` | `threshold` | critical | minimum 50 words | developer |
| `geo.sameContentForCrawlers` | `threshold` | critical | minimum 0.5 ratio | administrator |
| `geo.headingOutline` | `geo.headingOutline` | minor | none | editor |
| `geo.dateModified` | `geo.dateModified` | minor | none | developer |
| `geo.robotsPresent` | `geo.robotsPresent` | serious | none | administrator |
| `geo.llmsPresent` | `geo.llmsPresent` | minor | none | administrator |
| `readability.flesch` | `threshold` | moderate | minimum 50 points (a higher score reads easier) | editor |
| `readability.lix` | `threshold` | moderate | maximum 44 points (Danish, Norwegian and Swedish) | editor |
| `readability.longSentences` | `threshold` | minor | maximum 25 words | editor |
| `readability.longParagraphs` | `threshold` | moderate | maximum 80 words | editor |
| `readability.thinContent` | `threshold` | minor | minimum 300 words | editor |
| `readability.statistics` | `readability.statistics` | minor | none: a measure | none |
| `readability.headings` | `readability.headings` | minor | none: a measure | none |
| `ecodesign.pageWeight` | `ecodesign.pageWeight` | moderate | maximum 1024 kilobytes | developer |
| `ecodesign.requests` | `ecodesign.requests` | moderate | maximum 70 requests | developer |
| `ecodesign.domSize` | `ecodesign.domSize` | moderate | maximum 1500 elements | developer |
| `ecodesign.lazyLoading` | `ecodesign.lazyLoading` | moderate | none | developer |
| `ecodesign.imageFormats` | `ecodesign.imageFormats` | moderate | none | editor |
| `ecodesign.oversizedImages` | `ecodesign.oversizedImages` | moderate | none | editor |
| `html.*` | the id of the rule | see below | none | see below |
| `ecodesign.imageDimensions` | `ecodesign.imageDimensions` | moderate | none | developer |
| `ecodesign.webFonts` | `ecodesign.webFonts` | minor | maximum 2 files | developer |
| `ecodesign.autoplay` | `ecodesign.autoplay` | moderate | none | editor |
| `ecodesign.thirdParty` | `ecodesign.thirdParty` | moderate | maximum 5 hosts | developer |
| `ecodesign.heavyAssets` | `ecodesign.heavyAssets` | moderate | maximum 300 kilobytes | developer |
| `ecodesign.webVitals` | `webVitals` | minor | none: a measure, which always passes | developer |
| `links.broken` | `links.broken` | critical | none | editor |
| `links.hardcodedUrls` | `links.hardcodedUrls` | moderate | none | editor |
| `links.mixedContent` | `links.mixedContent` | serious | none | developer |
| `links.blankNoopener` | `links.blankNoopener` | minor | none | developer |
| `health.rawI18nKeys` | `health.rawI18nKeys` | critical | none | developer |
| `health.unpublishedChanges` | `health.unpublishedChanges` | serious | none | editor |
| `health.missingTranslations` | `health.missingTranslations` | serious | none | editor |
| `health.placeholderText` | `health.placeholderText` | serious | none | editor |

The rules `html.*` are the rules of the HTML check, and
[`html-rules.md`](../page-quality/html-rules.md) lists them. Each rule names a check of its own id,
and takes the subdomain `accessibility`.

Every rule whose id starts with `axe.` or `ecodesign.` runs in the browser. Each eco-design rule
names the check of one measure. A site that adds a rule of the same evaluator with another limit
gets a second result.
`ecodesign.webVitals` is a measure: it always passes, and its values are the figures of the Web Vitals
tab. A site switches it off in its layout to hide them.

Each check of axe-core that the drawer runs is a rule `axe.<id of the axe-core rule>`, such as
`axe.color-contrast`. Its evaluator is `axe`, and its severity is the impact of axe-core. A site
switches a check off in its layout, and axe-core then does not run it there. The check gives no
violation and no item to review, and the level scores leave it out. The drawer shows the severity of
the rule beside the impact of axe-core. It shows the help text of axe-core in the language of the
editor, unless an administrator writes a title on the rule in the system site.

Six rules that axe-core marks experimental ship switched off: `axe.css-orientation-lock`,
`axe.focus-order-semantics`, `axe.hidden-content`, `axe.p-as-heading`, `axe.table-fake-caption` and
`axe.td-has-header`. A site switches one on in its layout, and an administrator switches one on for
every site on its node in the system site.
