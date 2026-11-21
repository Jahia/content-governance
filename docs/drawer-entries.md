# Add an entry to the drawer from another module

Another module can add three kinds of entry to the drawer of content governance:

- a tab in the section bar,
- an action on a finding,
- a check that runs in the browser, on the preview of the page.

A module adds an entry to the registry of jContent (`@jahia/ui-extender`) when jContent starts. The
sections of content governance are tabs of the same registry, at the priorities 1, 2 and 3. Their
keys are `audit` for Page quality, `geo` for GEO readiness and `compliance`. A tab that another module
adds gets what these sections get:

- a switch in the site settings, which a site administrator turns off,
- its serious findings in "Fix first",
- a history of its findings per page and language.

Example: the AI assistant adds the action "Fix with the assistant" to each finding. On a Jahia with
no AI assistant, the action is not there.

## What an entry receives

Each entry receives three values from the drawer.

| Prop | Value |
|---|---|
| `path` | The path of the page or the main resource in the drawer, such as `/sites/digitall/home`. |
| `language` | The language of the content in the Jahia form, such as `en` or `zh_CN`. |
| `findings` | The list of the findings that the tabs of the drawer found so far. |

### A finding

A finding is an object with these fields:

| Field | Value |
|---|---|
| `rule` | The id of the rule that the finding reports, such as `audit.seo.shortTitle`. |
| `key` | The key of the finding. It is unique in the list. |
| `section` | The key of the tab that found it, such as `audit`, `geo`, `compliance` or the key of a tab of another module. |
| `tab` | The part of that tab that shows the finding, or `null` for a tab with no parts. |
| `severity` | `critical`, `serious`, `moderate` or `minor`, as axe-core names its impacts. |
| `title` | What is wrong, translated into the language of the editor's interface. |
| `detail` | What to do, translated. Only a finding of Page quality has this field. |
| `count` | The number of elements that fail the rule. Only an accessibility violation has this field. |

Today the id of a rule has one of these forms:

- `audit.a11y.<axe-core rule>`: an accessibility violation, such as `audit.a11y.image-alt`.
- `audit.<tab>.<recommendation>`: a recommendation of another tab of Page quality, such as
  `audit.seo.shortTitle`.
- `geo.<tab>`: a tab of GEO readiness that has something to review, such as `geo.crawler`.
- `compliance.<term>`: a term of a site glossary that the page uses.
- The key that a tab of another module gives its finding.

These ids can change. The issue
[content-governance#70](https://github.com/Jahia/content-governance/issues/70) gives each rule one
id, and the field `rule` will then carry that id.

### When the list of findings changes

A section runs its checks the first time the drawer shows it, and the drawer opens on its first
section. The list therefore grows while the editor opens the other sections. Each time the list
changes, the drawer renders each entry again with the new list.

The list holds the findings of the tabs that the page can use. A tab that is greyed out in the
section bar adds no finding.

## Add a tab

A tab is an entry of the type `contentGovernanceDrawerTab`, with the target `contentGovernanceDrawer`.

```js
import {registry} from '@jahia/ui-extender';
import {ReviewTab} from './ReviewTab';

registry.add('contentGovernanceDrawerTab', 'myModuleReview', {
    targets: ['contentGovernanceDrawer:10'],
    label: 'my-module:drawer.review.label',
    help: 'my-module:drawer.review.help',
    tabs: [{id: 'summary', label: 'my-module:drawer.review.summary', help: 'my-module:drawer.review.summaryHelp'}],
    component: ReviewTab
});
```

| Field | Value |
|---|---|
| key | The id of the tab, as the second argument. Give a key that names your module. |
| `targets` | `contentGovernanceDrawer:<priority>`. A lower priority puts the tab further to the left. Use 10 or more to come after the sections of content governance. |
| `label` | The name of the tab, as a translation key with its namespace. |
| `help` | The tooltip of the tab, as a translation key. Without it, the tooltip shows the label. |
| `settingsHelp` | What the site settings say about the tab, as a translation key. Without it, the settings show the help. |
| `tabs` | The parts of the tab that the site settings switch on or off, in order. Each part is `{id, label, help}`, with translation keys. A part with no id or no label is left out. |
| `requirePage` | `true` greys the tab out on a main resource that is not a page. |
| `requireWrite` | `true` greys the tab out for an editor who cannot write the page. |
| `component` | The React component that renders the content of the tab. |

A key and the id of a part start with a letter, then hold letters, digits, `_` or `-`, at most 64
characters in all. The site settings store these ids. The drawer leaves out a tab with another key,
and a tab with no label or no component.

The component receives `path`, `language` and `findings`, and these props:

| Prop | Value |
|---|---|
| `onCount(count)` | Shows a count badge on the tab. A count of 0 removes the badge. |
| `onFindings(findings)` | Gives the findings of the tab to "Fix first" and to the other entries. Call it again each time they change. |
| `isOn(part)` | `true` when the site keeps that part of the tab on. |
| `saveFindings(findings)` | Stores the findings as the latest run of the tab on the page. It returns a promise of the stored run, or of `null` when the server refused it. |
| `fetchHistory()` | Returns a promise of the stored runs of the tab on the page, newest first. |
| `settings` | The site settings, such as `settings.disabled`, the ids of the tabs and parts that the site switched off. |
| `requestedTab` | `{tab, at}` when "Fix first" opens a finding of the tab, so the tab shows the part `tab`. Otherwise `null`. |
| `onClose()` | Closes the drawer. |
| `onReopen()` | Opens the drawer again on this tab. |

The tab mounts the first time the editor opens it, and it stays mounted while the drawer is open. The
component can be a `React.lazy` component, and the drawer shows a loader while the code loads. If the
component throws an error, the tab shows a message and the console shows the error. The other tabs
of the drawer continue to work.

### The site switch

The site settings list every tab of the registry, with a switch for the tab and a switch for each of
its parts. The site stores the ids that a site administrator switched off, so a new tab is on until
then. A tab that the site switched off is not in the drawer. The server refuses to store or read its
history.

The drawer button shows when the site keeps at least one tab on, whatever module adds it.

### "Fix first"

"Fix first" lists the 5 most serious findings that are critical or serious, from the tabs that the
page can use. Findings of the same severity keep the order of the tabs. The drawer sets the field
`section` of each finding to the key of the tab, so "Open" in "Fix first" opens that tab. The tab
then receives the `tab` of the finding in `requestedTab`.

### The history

`saveFindings(findings)` stores the findings as one run of the tab, for every editor of the site. A
run holds these fields of a finding: `key`, `rule`, `tab`, `severity`, `title`, `detail` and `count`.
The server keeps the last 10 runs of each tab, per page and language. It keeps at most 500 findings
in a run, and cuts each text to 2000 characters.

The GraphQL API reads the runs of a tab:

```graphql
query {
  contentGovernance {
    pageAudits(path: "/sites/digitall/home", language: "en", tab: "myModuleReview") {
      tab auditedAt auditedBy
      summary { counts { tab count } total }
      results
    }
  }
}
```

`summary.counts` holds one count, under the key of the tab. The newest run also has `results`, which is
`{"findings": [...]}`. Without `tab`, `pageAudits` reads the runs of Page quality.

## Add an action on a finding

An action on a finding is a jContent action with the target `contentGovernanceFindingActions`. The
action receives `path`, `language`, `findings` and `finding`, which is the finding that the action
is on.

```js
registry.add('action', 'myModuleFix', {
    targets: ['contentGovernanceFindingActions:10'],
    buttonLabel: 'my-module:drawer.fix',
    onClick: ({path, language, finding}) => openFix({path, language, rule: finding.rule})
});
```

The drawer shows an action with no component as a small button:

- `buttonLabel` is the translation key of the label, and `buttonLabelParams` gives its parameters.
- `buttonIcon` is the icon of the button.
- `isVisible: false` hides the button, and `enabled: false` disables it.
- `onClick` receives all the props of the action as its first argument, and the click event as its
  second argument.

To show an action on some findings only, give it a component. The component receives the same props
and a `render` prop, as an action of jContent does. In this example, the action shows on the
findings of the SEO tab only:

```jsx
const SeoFix = ({render: Render, ...props}) => (
    props.finding.rule.startsWith('audit.seo.') ? <Render {...props}/> : null
);
```

The actions of a finding show in three places:

- after each finding of "Fix first",
- after each recommendation of the tabs of Page quality,
- in each accessibility violation of the Accessibility tab.

A finding of GEO readiness, of Compliance or of a tab of another module shows its actions in "Fix
first" only. "Fix first" lists the 5 most serious findings that are critical or serious.

If an action throws an error, that action disappears from the finding and the console shows the
error. The other actions of the finding stay.

## Add a check that runs in the browser

A check that needs a browser runs on the preview of the page in Page quality. Examples are axe-core,
the web vitals and eco-design. A check is an entry of the type `contentGovernanceCheck`, and it
applies the rules whose evaluator is its own. Content Governance registers axe-core, the web vitals
and eco-design this way, as another module does.

Example: a module measures the contrast of the brand colors. It ships the rule `acme.brandContrast`,
with the evaluator `acme.contrast`, and it registers this check:

```js
import {registry} from '@jahia/ui-extender';

registry.add('contentGovernanceCheck', 'acmeContrast', {
    evaluator: 'acme.contrast',
    run: async ({document, window, rules, language, signal}) => rules.map(rule => {
        const failing = [...document.querySelectorAll('h1, h2')].filter(element => lowContrast(window, element));
        return {
            ruleId: rule.id,
            passed: failing.length === 0,
            values: {color: '#ffcc00'},
            findings: failing.map(element => ({text: element.outerHTML, locator: cssPath(element)}))
        };
    })
});
```

In this example, `lowContrast` and `cssPath` are functions of the module.

| Field | Value |
|---|---|
| key | The id of the check, as the second argument. Give a key that names your module. |
| `evaluator` | The evaluator of the rules that the check applies. |
| `run` | A function that checks the preview and returns a list of results, or a promise of that list. |

The drawer leaves out a check with no evaluator or no `run` function, and the console shows why.

### The rule of a check

A rule of a check of the browser states `runtime: browser` in its file, or on its node in jContent.
The server never runs such a rule, and it never stops a publication on it. The site settings show
the rule as active. The file of the rule of the example is `acme.brandContrast.md`:

```markdown
---
id: acme.brandContrast
evaluator: acme.contrast
runtime: browser
subdomains: [accessibility]
severity: serious
label:
  en: "Contrast of the brand colors"
title:
  en:
    one: "{{count}} heading uses {{color}} on white"
    other: "{{count}} headings use {{color}} on white"
---
Use the dark variant of the brand color on a white background.
```

[The catalog of the rules](content-rules/catalog.md) states every key of a rule file. A module with no
Java code ships its rules and its check this way.

### What `run` receives

| Argument | Value |
|---|---|
| `document` | The document of the preview, with the editing tooling removed. |
| `window` | The window of the preview, for its performance entries and its styles. |
| `rules` | The rules of the evaluator that the site keeps on. Each rule has the fields of the GraphQL field `contentRules`, such as `id`, `severity`, `minimum` and `maximum`. |
| `language` | The language of the interface of the editor, such as `fr`. |
| `signal` | An `AbortSignal`. The drawer aborts it when the time limit of the check is over. |

The drawer runs the checks one after the other, in the order that the modules added them. A check
runs only when the site keeps at least one rule of its evaluator on, and keeps on the tab of the subdomain of
that rule. A rule of a subdomain that no tab shows never runs.

Before the first check, the drawer sets the performance mark `contentGovernanceChecks` in the preview.
A check that measures what the page loaded leaves out the resources after that mark, because other
checks added them. The script of axe-core is one of them.

### What `run` returns

`run` returns one result per rule, in the shape of the results of the checks of the server:

```js
{ruleId, passed, values, findings: [{text, context, locator, values}]}
```

| Field | Value |
|---|---|
| `ruleId` | The id of the rule. The drawer drops a result for a rule that the check did not receive. |
| `passed` | `true` when the page keeps the rule. Without it, a result passes when it has no finding. |
| `values` | What the check measured, such as `{color: '#ffcc00'}`. The title and the advice of the rule can name each value that is a text, a number or a flag, as `{{color}}`. |
| `findings` | One finding per place that breaks the rule. The drawer keeps the first 50. |
| `text` | What the finding shows, such as the HTML of an element. |
| `context` | An optional text beside it, such as what fails. |
| `locator` | An optional CSS selector of the element in the preview. With it, the finding offers "Highlight on the page". |

The drawer merges these results with those of the server. A failed result shows in the tab of the
subdomain of its rule, worded by its rule, like a finding of the server. Its count is the number of its
findings, unless `values` gives `count`.

### Time limit and failure

Each check has 60 seconds. A check that throws an error or runs out of time is left out. Its tabs
show why, with **Re-run**, and the other checks still run. The drawer never stores such a partial
audit.

### Not checked

A rule of the browser that no check gave a result for shows under **Not checked** in the tab of its
subdomain. No started module registers a check of its evaluator, or the check left the rule out. Such a
rule is neither passed nor failed.

## Limits

- The drawer reads the registry when it opens. If a module adds an entry after that time, the entry
  shows the next time the editor opens the drawer.
