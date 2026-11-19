# The test run of a draft rule: design

The author of a draft rule starts a test run on the pages that they choose. The test run applies the
draft rule and shows its findings to that author only. Every other editor, the site scan and the AI
agents read published rules only. Nothing on this page is built yet:
[content-governance#75](https://github.com/Jahia/content-governance/issues/75) tracks the work.

Example: a brand manager writes a writing rule that forbids "free trial". The brand manager tests the
rule on three pages, changes the pattern, tests again, then publishes the rule. No other editor sees
a finding of the rule before its publication.

## When a rule applies today

The module holds three stores of rules, and each store reads its own workspace.

| Store | Content type | Workspace that the checks read | Who gets a saved draft |
|---|---|---|---|
| Writing rules of the [editorial frame](../editorial-frame/guide.md) | `efnt:writingRule` | Live | Nobody. A rule applies after its publication. |
| Non-authorised terms of [Compliance](../compliance/glossary-guide.md) | `ccs:glossaryTerm` | Edit, with the session of the editor | Every editor, at the next scan |
| Rules of the [catalog](catalog.md) | `ccs:contentRule` | Edit | Every editor, at the next check |

The drawer does not apply the writing rules yet.
[content-governance#74](https://github.com/Jahia/content-governance/issues/74) adds the writing rules to the
Compliance section. Until then, ai-assistant and the tool `frame.check` of AI agents are the only
consumers of the writing rules.

## The design

The design below takes the recommended option of each choice in [Open choices](#open-choices).

1. **The entry point:** A jContent action "Test this rule" shows on a node of type
   `efnt:writingRule`, in the menu of the content list. The action opens a dialog.
2. **The dialog:** The author picks up to 10 pages of the site and clicks **Run**. The dialog lists
   the findings of each page: the content item, the property, the text around the match and the
   replacement of the rule. The author edits the rule, saves it without a publication and runs the
   test again.
3. **The server:** A read-only GraphQL field runs the test, with the path of the rule, the paths of
   the pages and the language. The field refuses the call when the user cannot write the rule node
   in the edit workspace, or cannot read a page. The field also refuses the call on a site that does
   not enable the module.
4. **The rule:** The server reads the rule node in the edit workspace, with the session of the user.
   It reads the fields as the frame reads a published rule, and applies the rule with the checker of
   the frame.
5. **The pages:** The server walks the text of each page in the same way as the Compliance scan. The
   walk covers the page, the content of the page, the content that the page references and the
   shared areas that the page shows. The walk reads the edit workspace, as the drawer does.
6. **The findings:** The response of the call holds the findings, and the server stores nothing, so
   only the author sees the findings. The frame service, the drawer, the site scan and the AI agents
   keep reading the live workspace.

The field is a query and writes nothing, so the CSRF guard does not apply to it.

## Open choices

The developer has not decided these choices yet. The first option of each choice is the
recommendation.

- **Where the test run starts:**
  - "Test this rule" on the rule, in jContent. This option works without content-governance#74.
  - A switch "Include my draft rules" in the Compliance section of the drawer, for one page at a
    time. This option needs content-governance#74 first.
  - A panel "Test draft rules" in the Compliance report of the site.
  - Both the action on the rule and the switch in the drawer.
- **Which rules a test run covers:**
  - Writing rules only. An issue then aligns the two other stores with "published rules only".
  - Writing rules and non-authorised terms. Compliance then reads published terms only.
  - Every rule, the catalog included. Page quality and GEO then read published catalog rules, and
    the module publishes the rules that it seeds. This option overlaps content-governance#79.
- **Who may run a test:**
  - A user who may edit the rule.
  - Only the user who created the rule node.
- **What a run shows:**
  - The findings of the tested rule only.
  - The findings of the published rules too, with the findings of the draft marked.
- **Which pages a run reads:**
  - Up to 10 pages that the author picks.
  - A page and the pages under it.
  - Every page of the site.
- **Which version of a page a run reads:**
  - The edit workspace, as the drawer reads it.
  - The live workspace, as visitors see it.
- **The dates of a rule:**
  - A run ignores `validFrom` and `validUntil`, so a rule that starts next month is testable now.
  - A run applies the dates, as the frame does.
- **A mandatory rule:**
  - A run reports each page whose text lacks the mandatory term.
  - A run skips a mandatory rule.
- **How many rules one run tests:**
  - One rule.
  - The rules that the author selects in the content list.
  - Every draft rule of a folder.
- **What stays after a run:**
  - Nothing, because the findings live in the dialog only.
  - The last run of each author, stored for that author only.

## What is left

- Decide the open choices.
- In the frame, add a public method that reads one rule node. The method that reads a rule is private
  to `FrameReader` today.
- Add the GraphQL field and its permission checks.
- Walk the text of a page. The walk of the Compliance scan is private to `ComplianceScanServiceImpl`,
  and content-governance#74 changes that class. Share the walk after content-governance#74 merges, or
  copy a small walk until then.
- Add the jContent action, and the dialog with a page picker and the list of findings. Translate
  their labels into the four languages of the module: `en`, `fr`, `de` and `es`.
- Write the unit tests of the server, and a Cypress spec. The spec checks that the author of a draft
  rule gets its findings on a chosen page, and that another editor gets none.
- Move this page to a guide for the brand manager, and update the sentence "Every consumer reads the
  live workspace" of the [frame guide](../editorial-frame/guide.md).
- Open the issue that aligns the non-authorised terms and the catalog rules, if the choice of rules
  stays on the writing rules only.
