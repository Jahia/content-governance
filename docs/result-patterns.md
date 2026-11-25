# The result patterns of the drawer

The drawer shows every result with five components: `ResultCard`, `PassedFold`, `FactBlock`,
`ScoreBlock` and `ResultBanner`. A rule of any module shows with them, and so does a tab of another
module. The code is in `src/javascript/common/results/`.

A rule picks its pattern with the key `view` of its file, as
[the catalog of the rules](content-rules/catalog.md#what-a-rule-holds) states. A tab of another module
reads the components from the registry of jContent:

```js
import {registry} from '@jahia/ui-extender';

const {ResultCard, PassedFold, FactBlock, ScoreBlock, ResultBanner} = registry.get('contentGovernanceApi', 'results');
```

A tab is the last resort. [Choose an entry](drawer-entries.md#choose-an-entry) lists what comes
first.

| Pattern | Use it for | Example |
|---|---|---|
| `ResultCard` | One rule that fails, with its occurrences. | "Open Graph tags are missing: og:image". |
| `PassedFold` | The rules of a section that passed, folded in one line. | "✓ 12 passed". |
| `FactBlock` | What a check measured, with no judgement. | The words and the reading time of a page. |
| `ScoreBlock` | The headline numbers of a section, each coloured by its band. | The share of the WCAG rules of each level that passed. |
| `ResultBanner` | The state of a section, and never a finding. | "Not run yet", with Run. |

## ResultCard

A card shows one rule that fails. Its level gives the colour of its left border and the icon that
opens its title line. The detail says what to do, and the occurrences fold under "Findings (N)". The
id of the rule shows at the bottom right. A rule of the default view, `view: card`, shows as a card when
it fails.

| Prop | Value |
|---|---|
| `level` | `serious`, `moderate`, `minor`, `passed` or `review`. The function `levelOf` of `levels.js` turns a severity into a level. |
| `title` | The title of the finding. |
| `detail` | What to do, in one or two sentences. |
| `rule` | The id of the rule, such as `seo.openGraph`. |
| `chips` | Marks at the start of the title, such as a WCAG level. |
| `occurrences` | A list of `{key, content, action, below}`: one row per occurrence, with one action, such as Highlight. `below` shows under the row, across its whole width. |
| `occurrencesLabel` | The label of the folded occurrences, "Findings (N)" by default. |
| `occurrencesAction` | An action at the end of the line "Findings (N)", such as Generate all. |
| `occurrencesHeader` | What shows above the open occurrences, such as the progress of Generate all. |
| `isOpen` | Opens the occurrences when it turns `true`. |
| `action` | The action of a rule with no occurrence, on the title line. |
| `menu` | The actions at the end of the title line, such as the ⋯ of the actions of other modules. |
| `children` | More about the finding, between the head and the occurrences. |
| `isCompact` | `true` for a passed rule in a `PassedFold`: one line, with no border. |

In the drawer, the card of a finding also shows the fixers of other modules that apply to it.
[Add a fixer of a finding](drawer-entries.md#add-a-fixer-of-a-finding) states how a module adds one.

## PassedFold

The fold shows the rules of a section that passed, as one line that opens to their compact cards.

| Prop | Value |
|---|---|
| `items` | A list of `{key, title, detail, rule}`. The fold shows nothing when the list is empty. |

## FactBlock

The block shows label and value lines under an optional title. A problem among the values is a card
of its rule, and never a mark in the block. A rule with `view: fact` shows its values this way. The
title is the title of the rule, and each value is one row, labelled by the `valueLabels` of the rule.

| Prop | Value |
|---|---|
| `title` | The title of the block. |
| `rows` | A list of `{key, label, value, action, indent}`. `action` is one action on the row, and `indent` the steps of indentation, such as the level of a heading. |
| `note` | One line under the rows, such as "20 of 34 shown". |

## ScoreBlock

The block shows one tile per number, coloured by its band. A rule with `view: score` shows its values
this way. A number inside the `minimum` and the `maximum` of the rule is `good`, and a number outside
them is `poor`. A rule with no limit gives no band.

| Prop | Value |
|---|---|
| `title` | The title of the block. |
| `items` | A list of `{key, label, value, caption, hint, band}`. `band` is `good`, `average` or `poor`. `caption` says what the value counts, such as "automated rules passed". |

## ResultBanner

The banner shows the state of a section: a run under way, a run that failed, a check that is not
configured, or "No issues". It holds no finding, because a finding is a card. The title is a few
words, and a sentence goes in the text.

| Prop | Value |
|---|---|
| `variant` | `neutral`, `info`, `warning`, `danger` or `success`. `success` is a neutral banner with a check mark. |
| `title` | A few words, such as "Not run yet". |
| `children` | The text of the banner. |
| `action` | One action, such as Run or Re-run. |

Example: the section of a subdomain with a rule on demand shows the banner "Not run yet" with Run.
After a run, the banner gives the date of the last run and who ran it, with Run again.
