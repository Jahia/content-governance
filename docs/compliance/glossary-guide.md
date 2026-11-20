# Term rules, for the people who maintain the terms

A term rule is a content rule of the evaluator `pattern`. It names a word or a phrase that the content
of a language must not hold, or must write in another way. Each team keeps its terms in a folder of rules
with its own permissions. Example: the legal team keeps "guaranteed" in its folder, and the brand team
keeps "seamless" in another folder that the legal team cannot change.

Three places check the term rules of the language of the content, with the same rules and the same
matching:

- the **Compliance** section of the **Content governance** drawer checks one page
  ([`page-scan-guide.md`](page-scan-guide.md));
- the **Compliance** dashboard under the **Additional** accordion of jContent checks the whole site
  ([`dashboard-guide.md`](dashboard-guide.md));
- the tool `frame.check` of the MCP server checks a text that an agent writes.

Each finding names the id of its rule and the folder of the rule. Example: `compliance.guaranteed`
in `contents/term-lists/legal`.

The Compliance section and the dashboard need the Content Governance module on the site and the
Compliance section on in **Site Settings > Content governance**. The section is on by default. A site
that switches the section off keeps its rules, and the drawer does not read them until the section is
on again.

## The example rule

When Content Governance is enabled on a site, it creates one list to start from:
**Content Folders > Compliance glossaries** (`contents/compliance-glossaries`). This folder holds a
folder for the default language of the site, with one example rule. The rule is "click here" in
English and "cliquez ici" in French. A language that the module does not translate gets the English
text. The id of the
example rule is `compliance.example-term`.

The example is not published, because the checks read the rules in the edit workspace. Rename it, add
your rules and delete the example. The module creates it once for each site, so it does not come back.

## Create a list of terms

A list of terms is a content folder that holds rules. Each list has the permissions of its folder.

1. In jContent, open **Content Folders** and select a folder under `contents` of the site.
2. Create a folder for the list, such as `term-lists/legal`.
3. In the folder, set the roles of the team that keeps the list. Example: give the role `editor` on
   `term-lists/legal` to the legal team only.
4. Optionally, restrict the folder to rules: in **Content types allowed**, select **Content rule**.

A team that has no role on a folder cannot change its rules. The rules of that folder still apply to
the content of the team. The checks read every rule with a system session, so a rule applies even
to an editor who cannot read its folder.

## Add a term rule

Open the folder and create a **Content rule**. One rule holds one word or phrase and its other forms.

| Field | What to enter |
|---|---|
| Rule id | A unique id with a dot, such as `compliance.guaranteed`. The part before the first dot is the domain of the rule |
| Evaluator | `pattern` |
| Kind | **Forbidden term** when the term has no replacement. **Term to replace** when the editor writes the replacement instead |
| Term or pattern | The word or phrase, as you write it. The next section states the stars |
| Match | **Whole word or phrase, any case and accents** for a term |
| Replacement | What to write instead. Editors see it under **Write instead** |
| Language | The language of the content that the rule checks, such as `en`. Empty: every language |
| Other forms of the term | Other ways to write the term, such as the plural "guarantees" |
| Severity | **Serious** for a legal term. The drawer ranks the findings by severity |
| Advice | Why the term is not allowed. Editors read it in the report, so write it for them |

A rule of the kind **Mandatory mention** needs a whole page. The Compliance section checks each value
on its own, so it does not report a mandatory mention.

## Several lists for one language

- Every rule of the language applies, whatever its folder. The reports list the folders under
  **Rule folders applied:**.
- Every finding names its rule id and its folder, in the drawer section, in the dashboard and in the
  CSV export. The CSV export has a `ruleId` column and a `glossary` column for the folder.
- When two folders hold the same term, a text that holds the term gets one finding for each rule.
  Each finding has the advice of its own rule, so the legal team and the brand team both see their
  rule.

## How matching works

- **Case and accents are ignored.** "gratuité" also finds "Gratuite" and "GRATUITÉ".
  A typographic apostrophe (’) counts as a straight one ('), and "œ" and "æ" count as "oe" and "ae".
- **A phrase matches across any white space**, which includes a line break and a non-breaking space.
  Example: "satisfait ou remboursé" finds "satisfait&nbsp;ou remboursé".
- **A star at an end of the term** decides how much of a word must match:

| Term | Finds | Does not find |
|---|---|---|
| `best` | "the best talent" | "bestseller" |
| `guarant*` | "guarantee", "guaranteed" | "unguaranteed" |
| `*free*` | "free", "carefree" | |

- **Japanese, Chinese, Thai, Lao, Khmer and Burmese** write words without spaces. In these scripts,
  a term matches wherever it is written, such as 安全 in 安全なロック機能, and a star after the term
  matches the term itself. Kana keep their voiced marks, so ガイド is not カイト. A fullwidth Latin
  letter counts as a plain letter, so ＦＲＥＥ is "free".
- **Rich text is read as the visitor reads it.** Formatting inside a word does not hide it, so
  `<b>gra</b>tuit` is "gratuit". An HTML tag or attribute never matches.
- **A match inside the replacement is the right term.** Example: with the rule "mail" to "e-mail", the
  text "e-mail" passes. This search keeps the accents. With the rule "role" to "rôle", the text "rôle"
  passes and the text "role" gets a finding.
- **The Match value Regular expression** reads the text as it is written, with no folding.

## Glossary terms to translate

A glossary term to translate is a term rule of the language of the translation. Its term is in another
language, which the field **Language of the term (glossary)** names. Example: the rule
`glossary.access-token` of `fr` replaces "access token" (`en`) with "jeton d'accès".

A French text that holds "access token" gets a finding with the replacement "jeton d'accès". The tool
`frame.check` also checks a translation against its source. If the source holds "access token" and
the translation lacks "jeton d'accès", the tool reports the missing term.

The brand can also forbid a translation, with a rule of the language of the translation. Example: the
rule of `fr` replaces "jeton d'authentification" with "jeton d'accès".

## Things to know

- **No need to publish a rule for the drawer.** The Compliance section reads the version that you
  edit, so a new rule applies at the next check. The tool `frame.check` reads the published rules.
- **A language with no term rule is "Not checked"**, never "Compliant". The reports say that nothing
  was checked and how to fix it.
- **Keep the rules in the content folders.** A rule that is placed on a page applies too, and an
  editor of the page can change it.

## On an instance that held glossaries

Before content-governance#74, the terms lived in two other content types: the Compliance glossaries
(`ccs:glossary` and `ccs:glossaryTerm`) and the glossary of the editorial frame (`efnt:glossaryTerm`).
When the module starts, it converts each node of these types into a content rule in place, in the edit
and the live workspace. Each node keeps its identifier, its place and its permissions, so each list
keeps the permissions of its folder. The conversion is one-way: an older version of the module cannot
read the converted nodes. A backup restores the old nodes.

| Old node | Content rule |
|---|---|
| A term of a Compliance glossary | The rule `compliance.<node name>`, serious, of the language of its glossary, whose advice is the Reason. A term with a Suggested wording becomes a **Term to replace**, and the wording becomes the replacement |
| A Match of a Compliance term | **Whole word or phrase** keeps the term, **Word starting with** adds a star at the end, and **Anywhere** adds a star at each end |
| A Compliance glossary | A content folder that holds rules, with its title and its description |
| An approved term of the frame | The rule `glossary.<node name>`, moderate, of the language of the translation. It replaces the term with the translation |
| A forbidden translation of the frame | The rule `glossary.<node name>`, moderate, of the language of the translation. It replaces the forbidden translation with the approved one. With no approved translation, it forbids the term |

The other forms of a term and of a translation become the other forms of the term and of the
replacement. The note and the definition of a frame term become the advice. An id that the site
already has is not used twice. The new rule then takes the first 8 characters of the node identifier
after its domain, such as `glossary.67d3bc8a`.

## On an instance that had Content Compliance Scan

Content Governance replaces the Content Compliance Scan module and keeps its glossaries where they
are. From Content Governance 1.1.0, the glossary types are taken over when the module starts. Since
content-governance#74, the module converts these glossaries into rules, as the previous section
states. Why the types needed a takeover:
[`architecture.md`](architecture.md#migration-from-content-compliance-scan).
