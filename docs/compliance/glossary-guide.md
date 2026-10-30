# Glossaries, for the people who maintain the terms

A glossary is the list of words and phrases that must not appear in the content of one language of
the site. Each scan checks the content of one language against every glossary of that language:

- the **Compliance** section of the **Content governance** drawer checks one page
  ([`page-scan-guide.md`](page-scan-guide.md));
- the **Compliance** dashboard under jContent's **Additional** accordion checks the whole site
  ([`dashboard-guide.md`](dashboard-guide.md)).

Both need the Content Governance module enabled on the site and the Compliance section switched on in
**Site Settings > Content governance** (it is on by default). A site that switches the section off
keeps its glossaries; nothing reads them until the section is on again.

## The example glossary

When Content Governance is enabled on a site that has no glossary yet, it creates one to start from:
**Content Folders > Compliance glossaries** (`contents/compliance-glossaries`), holding a glossary in
the site's default language with one example term ("click here" in English, "cliquez ici" in
French; English text for a language the module has no translation for). The folder only offers
**Compliance glossary**, and a glossary only offers **Non-authorised term**. The example is not
published (scans read glossaries in the edit workspace). Rename it, add your terms and delete the
example term: it is created once per site, so it does not come back.

## Create a glossary

1. In jContent, open **Content Folders** and pick a folder under the site's `contents` (any folder,
   any depth; a dedicated `compliance-glossaries` folder keeps things tidy).
2. **New content > Compliance > Compliance glossary**.
3. Fill in:

| Field | What to enter |
|---|---|
| Title | The name editors see in the reports ("Legal - claims and wording (EN)") |
| Language | The site language the glossary applies to. The list shows every language of the site, live or not |
| Description | Who maintains it and what it covers. Never shown to visitors |

You can have several glossaries for the same language (one from Legal, one from Brand): a scan
applies them all and says which ones it applied.

## Several glossaries for one language

- Every glossary of the language applies; the reports list them under **Glossaries applied:**.
- When more than one applies, every issue names its glossary (the drawer section, the dashboard and
  the CSV export, which also has a `glossary` column).
- The same term in two glossaries is reported once **per glossary**, each with its own reason and
  suggested wording: Legal and Brand both see their rule. The counts then include both.
- Within one glossary, two entries that find the same text are reported once.

## Add terms

Open the glossary and use **New Non-authorised term**. One entry per word or phrase:

| Field | What to enter |
|---|---|
| Term | The word or phrase, as you would write it |
| Match | How the term is found (below) |
| Reason | Why it is not authorised. Editors read it in the report, so write it for them |
| Suggested wording | What to write instead. Editors see it under **Write instead**, next to the reason |

The terms list in jContent shows each entry by its term.

## How matching works

- **Case and accents are ignored.** "gratuité" also finds "Gratuite" and "GRATUITÉ".
  Typographic apostrophes count as straight ones (’ and '), and "œ"/"æ" as "oe"/"ae".
- **Phrases match across any white space**, line breaks and non-breaking spaces included:
  "satisfait ou remboursé" finds "satisfait&nbsp;ou remboursé".
- **Match** decides how much of a word must match:

| Match | Term | Finds | Does not find |
|---|---|---|---|
| Whole word or phrase (default) | `best` | "the best talent" | "bestseller" |
| Word starting with | `guarant` | "guarantee", "guaranteed" | "unguaranteed" |
| Anywhere, inside words too | `free` | "free", "carefree" | |

- **Japanese, Chinese, Thai, Lao, Khmer and Burmese** write words without spaces: there, a term is
  found wherever it is written (安全 in 安全なロック機能), and *Word starting with* matches the term
  itself. Kana keep their voiced marks (ガイド is not カイト), and fullwidth Latin letters count as
  plain ones (ＦＲＥＥ is "free").
- Rich text is read as the visitor reads it: formatting inside a word does not hide it
  (`<b>gra</b>tuit` is "gratuit"), and HTML tags or attributes never match.

## Things to know

- **No need to publish a glossary.** Scans read the version you are editing, so a new term applies
  at the next scan.
- **Editors must be able to read the glossaries.** A scan reads everything with the rights of the
  person who runs it: a glossary someone cannot read does not apply to their scans. Keep glossaries in
  a folder every editor can read.
- **The Language list shows every language of the site**, also one not yet active on the live
  site: check a language's content before it goes live.
- **Keep glossaries in the content folders.** The type is also offered when adding content to a
  page in Page Builder (content folders accept only droppable types); a glossary placed on a page
  is never read by the scans.
- **A language with no glossary is "Not checked"**, never "Compliant": the reports say that nothing
  was checked and how to fix it.

## On an instance that had Content Compliance Scan

Content Governance replaces the Content Compliance Scan module and keeps its glossaries where they
are, with the same content types. Existing glossaries and terms carry over: nothing to export or
recreate, and scans find exactly what they found before.

From Content Governance 1.1.0, the glossary types are taken over when the module starts, and the
upgrade needs nothing more. With 1.0.0, on an instance where Content Compliance Scan was installed
before, **New content** did not offer **Compliance glossary** and **Non-authorised term**, and
jContent showed the types and their fields by their technical names with a generic icon; existing
glossaries kept working and scanning. Installing 1.1.0 fixes it. Why it happened:
[`architecture.md`](architecture.md#migration-from-content-compliance-scan).

What the technical names stand for:

| Technical name | Label on a fresh install |
|---|---|
| `complianceContent` (category of the New content menu) | Compliance |
| `glossary` | Compliance glossary |
| `language` | Language |
| `description` | Description |
| `glossaryTerm` | Non-authorised term |
| `term` | Term |
| `matchMode` | Match |
| `word`, `prefix`, `anywhere` (values of Match) | Whole word or phrase, Word starting with, Anywhere, inside words too |
| `reason` | Reason |
| `alternative` | Suggested wording |

The field tooltips are missing in that case too. The tables above describe each field.
