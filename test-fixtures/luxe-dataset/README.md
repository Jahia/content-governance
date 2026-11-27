# The luxe quality dataset

Test pages on the demo site `luxe` that break the rules of content-governance on purpose. Each page
breaks one family of rules: accessibility, search engines, readability, Jahia health, AI crawlers or
compliance. Use them to try the drawer by hand, to demo it, or to check a change of a rule.

Example: open **Quality lab > Villas on the French Riviera** in jContent, open the drawer, and
**Page quality > Accessibility** lists two images without a text alternative.

## What it needs

- A Jahia with the site `luxe` (template set `luxe-jahia-demo`) in English and French.
- content-governance enabled on `luxe`.
- ai-assistant, for the fixers and the AI rules. Without it, the drawer shows the findings and no
  fix.
- A user with the server administration, for the provisioning API. `root` by default.

## Install, reset and remove

```bash
export JAHIA_URL=https://my-jahia.example.com JAHIA_USER=root JAHIA_PASSWORD=<password>
python3 test-fixtures/luxe-dataset/dataset.py install
```

`install` removes the dataset first, then writes it again. So `reset` is the same command: run it
after a test that fixed a page, and the errors are back.

| Command | What it does |
|---|---|
| `install` or `reset` | Writes the pages and the terms, publishes the pages except `draft-offer`, and checks that the script ran to its end. |
| `check` | Runs the page check and the compliance scan of each page, in English and in French, and compares the findings with `expected.json`. It exits with 1 when an expected finding is missing. |
| `remove` | Removes the pages and the terms, in the edit and the live workspaces. |

Without Python, run `install.groovy` or `remove.groovy` in `/modules/tools/groovyConsole.jsp`, or
through a provisioning manifest:

```yaml
- executeScript: "install.groovy"
```

The scripts write only under `/sites/luxe/home/quality-lab` and `/sites/luxe/contents/quality-lab-terms`.

## The pages

The pages sit under `/sites/luxe/home/quality-lab`. Each one has an English and a French version,
except `jahia-health`.

### Quality lab (the control page)

The reference page. It follows the rules that an editor can fix. It lists the other pages.

Expected: only the findings that the template gives on every page (see
[Findings that every page gives](#findings-that-every-page-gives)).

### Accessibility: `accessibility`, "Villas on the French Riviera"

The `axe.*` findings come from the browser check of the drawer. `dataset.py check` does not run it.

| Planted error | Finding | Fix in the drawer |
|---|---|---|
| Two images in the text with no `alt` | `html.imageAltMissing`, `axe.image-alt` | **Generate alt** |
| An `h1` in the text, below the `h1` of the header | `html.headingH1Multiple` | |
| An `h4` right after an `h2` | `html.headingLevelSkipped`, `axe.heading-order` | |
| An empty `h3` | `html.headingEmpty`, `axe.empty-heading` | |
| Links "click here" and "read more" ("cliquez ici", "lire la suite") | `html.linkTextVague`, and the compliance term `compliance.example-term` (`compliance.luxe-cliquez-ici` in French) | |
| A link whose only content is an image with `alt=""` | `html.linkNameMissing`, `axe.link-name` | |
| A price table with no header row | `html.tableHeaderMissing` | |
| Light grey text (`#c9c9c9`) on white | `axe.color-contrast` | |
| Two paragraphs with the id `riviera-contact` | `html.duplicateId` | |
| An email field with no label, and a button with no text | `html.formLabelMissing`, `html.buttonNameMissing` | |
| A link that opens a new tab with no `rel="noopener"` | `links.blankNoopener` | |

### Search engines: `seo`, "Apartments for sale in Geneva with a view of the lake and the Alps"

| Planted error | Finding | Fix in the drawer |
|---|---|---|
| A title of 83 characters with the site name | `seo.title` | **Suggest** |
| A meta description of 226 characters | `seo.metaDescription` | **Suggest** |
| No Open Graph image | `seo.openGraph` | |
| A link typed as `http://localhost/sites/luxe/home/buy.html` | `links.hardcodedUrls`, `links.mixedContent` | |
| A link to `http://www.ge.ch/` | `links.mixedContent` | |
| A link to `draft-offer`, a page that is never published | `links.broken` (`not_published`) | |

### A page with almost no text: `thin-page`, "Monaco"

| Planted error | Finding | Fix in the drawer |
|---|---|---|
| A title of 23 characters with the site name | `seo.title` | **Suggest** |
| No meta description | `seo.metaDescription` | **Suggest** |
| No Open Graph image | `seo.openGraph` | |
| 39 words of text | `readability.thinContent` | |

### Readability: `readability`, "The Paris property market in 2026"

| Planted error | Finding | Fix in the drawer |
|---|---|---|
| Sentences of 50 to 100 words | `readability.longSentences`, one finding per sentence | **Rewrite** |
| Paragraphs of more than 80 words on average | `readability.longParagraphs` | |
| Jargon and long words: a Flesch score of 0 | `readability.flesch` | |

### Jahia health: `jahia-health`, "Penthouse on the Upper East Side"

English only, on purpose.

| Planted error | Finding |
|---|---|
| No French version of any item | `health.missingTranslations` |
| "Lorem ipsum" and "TODO" | `health.placeholderText` |
| The text `luxe:label.floorPlan.download`, a label key | `health.rawI18nKeys` |
| `late-addition`, an item added after the publication | `health.unpublishedChanges` (`not_published`) |
| `late-change`, a text changed after the publication | none, although jContent shows the item as modified. See [Known gaps](#known-gaps). |

### AI crawlers: `geo-members-only`, "Off-market listings for members"

The guest user may not read this page (`DENY reader` for `u:guest`). Run **SEO & GEO > Run check**
on it.

| Planted error | Finding |
|---|---|
| A visitor with no account cannot read the page | `geo.guestReadable` |
| Every crawler gets a 404 | `geo.reachable`, `geo.policyMatchesReality`, then the checks of the content, which read the 404 page |

Compare with any other page of the lab, which every crawler reads.

### Compliance: `compliance`, "Investing in prime real estate"

The terms are in `/sites/luxe/contents/quality-lab-terms`. content-governance reads them in the edit
workspace, so they stay unpublished.

| Planted error | Finding | Kind |
|---|---|---|
| "guaranteed return" / "rendement garanti" | `compliance.luxe-guaranteed-return`, `compliance.luxe-rendement-garanti` | Forbidden, critical, **blocking** |
| "risk-free" / "sans risque" | `compliance.luxe-risk-free`, `compliance.luxe-sans-risque` | Forbidden |
| "tax-free" | `compliance.luxe-tax-free` | Replace with "tax-efficient" |
| "best price" / "meilleur prix" | `compliance.luxe-best-price`, `compliance.luxe-meilleur-prix` | Forbidden |
| "click here" / "cliquez ici" | `compliance.example-term`, `compliance.luxe-cliquez-ici` | Replace |

The two "guaranteed return" terms block a publication. The script publishes the page before it
writes them. To try the block, change a word of the page and publish it.

### `draft-offer`, "Draft offer: a chalet in Megève"

Never published. It is the target of the broken link of `seo`.

## Findings that every page gives

The luxe template gives these on every page, the control page included. The dataset does not plant
them, and `dataset.py check` does not print them.

- `seo.structuredData` and `geo.dateModified`: luxe renders no JSON-LD.
- `links.blankNoopener`: three links of the footer open a new tab with `rel="noreferrer"` only.
- `html.frameTitleMissing`: the persona panel of the preview, an `iframe` with no title.
- `geo.robotsPresent`, `geo.llmsPresent`, `geo.namedInRobots`: when the site serves no `robots.txt`
  and no `llms.txt`.

An instance can add its own. On catelyn, the menu of luxe lists unpublished pages of other tests, so
each page gives `links.broken`.

## Fixing a finding

The fixers come from ai-assistant. Each one asks a model for a text, and the editor copies it into
Content Editor: no fixer writes the content.

1. Open a finding with a fixer, such as an image of `accessibility`, and click **Generate alt**.
2. Copy the proposal.
3. Edit the text item, paste the proposal, save.
4. Run the check again: the finding is gone.
5. Run `dataset.py reset` to plant the error again.

The AI rules of ai-assistant (`ai.*`, run with their **Run** button) give findings with a proposed
fix on the same pages.

## Known gaps

- The `html-filtering` module of luxe refuses `<iframe>`, `<script>` and `<meta>` in a rich text. So
  the dataset cannot plant `html.frameTitleMissing` in the content, nor `seo.indexable`,
  `seo.followLinks` or `seo.structuredDataValid`. The template gives `html.frameTitleMissing` anyway.
- `html.pageLanguageMissing` and `html.pageLanguageMismatch` cannot be planted: the template sets
  `lang` from the language of the page.
- `health.unpublishedChanges` misses a text changed after the publication: `jahia-health/main/late-change`
  passes, though jContent shows it as modified. The check reads the status of the item, and Jahia
  gives the change to the translation node of the language.
