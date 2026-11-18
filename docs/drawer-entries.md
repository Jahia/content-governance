# Add an entry to the drawer from another module

Another module can add two kinds of entry to the drawer of content governance:

- a tab in the section bar, after the sections of content governance,
- an action on a finding.

A module adds an entry to the registry of jContent (`@jahia/ui-extender`) when jContent starts. If no
module adds an entry, the drawer shows its own sections only.

Example: the AI assistant adds the action "Fix with the assistant" to each finding. On a Jahia with
no AI assistant, the action is not there.

## What an entry receives

Each entry receives three values from the drawer.

| Prop | Value |
|---|---|
| `path` | The path of the page or the main resource in the drawer, such as `/sites/digitall/home`. |
| `language` | The language of the content in the Jahia form, such as `en` or `zh_CN`. |
| `findings` | The list of the findings that the checks of the drawer found so far. |

### A finding

A finding is an object with these fields:

| Field | Value |
|---|---|
| `rule` | The id of the rule that the finding reports, such as `audit.seo.shortTitle`. |
| `key` | The key of the finding. It is unique in the list. |
| `section` | The section that found it: `audit`, `geo` or `compliance`. |
| `tab` | The tab of the section that shows the finding, or `null` for a section with no tabs. |
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

These ids can change. The issue
[content-governance#70](https://github.com/Jahia/content-governance/issues/70) gives each rule one
id, and the field `rule` will then carry that id.

### When the list of findings changes

A section runs its checks the first time the drawer shows it, and the drawer opens on its first
section. The list therefore grows while the editor opens the other sections. Each time the list
changes, the drawer renders each entry again with the new list.

The list holds the findings of the sections that the page can use. A section that is greyed out in
the section bar adds no finding.

## Add a tab

A tab is an entry of the type `contentGovernanceDrawerTab`, with the target `contentGovernanceDrawer`.

```js
import {registry} from '@jahia/ui-extender';
import {ReviewTab} from './ReviewTab';

registry.add('contentGovernanceDrawerTab', 'myModuleReview', {
    targets: ['contentGovernanceDrawer:10'],
    label: 'my-module:drawer.review.label',
    help: 'my-module:drawer.review.help',
    component: ReviewTab
});
```

| Field | Value |
|---|---|
| key | The id of the tab, as the second argument. Give a key that names your module. |
| `targets` | `contentGovernanceDrawer:<priority>`. A lower priority puts the tab further to the left. |
| `label` | The name of the tab, as a translation key with its namespace. |
| `help` | The tooltip of the tab, as a translation key. Without it, the tooltip shows the label. |
| `component` | The React component that renders the content of the tab. |

The drawer leaves out a tab whose key is `audit`, `geo` or `compliance`, and a tab with no label or no
component.

The component receives `path`, `language` and `findings`, and one more prop:

- `onCount(count)` shows a count badge on the tab. A count of 0 removes the badge.

The tab mounts the first time the editor opens it, and it stays mounted while the drawer is open. The
component can be a `React.lazy` component, and the drawer shows a loader while the code loads. If the
component throws an error, the tab shows a message and the console shows the error. The other tabs
of the drawer continue to work.

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

A finding of GEO readiness or of Compliance shows its actions in "Fix first" only. "Fix first" lists
the 5 most serious findings that are critical or serious.

If an action throws an error, that action disappears from the finding and the console shows the
error. The other actions of the finding stay.

## Limits

- The drawer reads the registry when it opens. If a module adds an entry after that time, the entry
  shows the next time the editor opens the drawer.
- The site settings of content governance do not list an added tab, so a site administrator cannot
  switch it off there. The module that adds the tab decides what its component shows.
- The button of the drawer shows only when the site keeps at least one section of content
  governance switched on. An added tab does not make the button show.
