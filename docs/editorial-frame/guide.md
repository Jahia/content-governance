# The editorial frame

Content Governance holds the editorial frame of a brand, and checks texts against it with no model:

- the **writing rules**: forbidden terms, terms to replace, style patterns and mandatory mentions;
- the **glossary**: how the brand writes a term in another language, checked on each translation.

Until version 1.12, a separate module named editorial-frame held this code. The Java packages, the
content types and the tool name keep their names. [`architecture.md`](architecture.md) states how
the frame sits in the module.

The glossary of the frame and the terms of the Compliance section are one model since
content-governance#74: term rules of the check `pattern`, which
[`../compliance/glossary-guide.md`](../compliance/glossary-guide.md) describes. The Compliance section
and `frame.check` run the term rules through the same check, so they give the same findings for the
same text.

## Where the frame lives

The frame is content of the site. These types hold it:

| Type | What it holds | Edited by |
|---|---|---|
| `ccs:contentRule` | A writing rule is a [content rule](../content-rules/catalog.md) of the check type `PATTERN` and the check `pattern`: `kind` (forbidden, replace, mandatory, style), `pattern`, `matchMode` (term or regex), `replacement`, `ruleLanguage`, `severity` with `blocking`, `advice` (translated), `wrongExamples` and `rightExamples`, `owner`, `decidedOn`, `validFrom` and `validUntil`. It replaces `efnt:writingRule`, which the module converts when it starts. | Legal, brand manager |
| `efnt:glossaryTerm` | Replaced by a `ccs:contentRule` of the language of the translation, which the module converts when it starts. The rule names the language of its term in `patternLanguage`, and its other forms in `patternForms` and `replacementForms`. | Brand manager, translators |

The service finds these nodes by their type anywhere under the site, so no folder is imposed. The
frame of `systemsite` is the frame of the group: every site reads it first. Its rules and glossary
terms add up with those of the site. The checks of a text read the rules of `systemsite` too, through
the check registry.

### The glossary

A glossary term says how the brand writes a term in another language. For example, "access token"
in `en` is "jeton d'accès" in `fr`. A glossary term is a replace rule of the language of the
translation, whose `patternLanguage` names the language of the term. The frame of a language holds
the terms whose translation is in that language:

- `frame.check` on a text of the language reports the term as a replace rule: "access token" in a
  French text comes with kind `replace` and the replacement "jeton d'accès".
- `frame.check` with `sourceText` and `sourceLocale` checks a translation: where the source holds a
  term, the translation must hold its rendering. A miss comes with kind `glossary`, and the rendering
  in `missing`. The check counts no occurrence.
- A term and its rendering match in any of their forms. With the forms "access tokens" and
  "jetons d'accès", the translation of "Create access tokens" as "Créez des jetons d'accès" passes.
  An apostrophe matches a straight or a curly one.
- A translation that the brand never writes, such as "jeton d'authentification" for "access token",
  is a replace rule of `fr` toward "jeton d'accès". Any French text that holds it comes with kind
  `replace`. A translation that holds it gets no second finding for the missing rendering.

`fr` and `fr_FR` are the same language for the glossary.

### The examples and the dates of a rule

A writing rule can carry texts that break it, `wrongExamples`, and texts that keep it,
`rightExamples`. `owner` and `decidedOn` record who decided the rule, and on which day.

A rule applies from its `validFrom` day to its `validUntil` day, both included, in the time zone of
the server. An empty date sets no limit. Out of its dates, a rule is not part of the frame: the
checks leave it out. For example, a ban on competitor names valid until 2026-12-31 stops on
2027-01-01 with no change in jContent.

The frame, the glossary and the instructions of a model are read in the **live** workspace. A change
reaches them only after its publication, through the workflow of the site. A check of a text, such
as `frame.check`, reads the rules in the edit workspace, as the drawer and the Compliance scan do. A
new rule therefore applies at the next check. The mandatory mentions of a whole document come from
the published frame.

## What reads the frame

| Consumer | How |
|---|---|
| ai-assistant | The rules and the glossary go into the instructions of each turn. The approval dialog lists each rule that a proposed value breaks, and each mandatory term that a whole page lacks. |
| MCP clients (Claude, Copilot) | The read-only tool `frame.check`. The module adds it only when the module mcp-server is installed. |
| Other modules | The OSGi service `org.jahia.pm.modules.editorialframe.api.EditorialFrameService`. |

## The service

The OSGi service `EditorialFrameService` gives the frame to the other modules:

| Method | Gives |
|---|---|
| `getFrame(site, language)` | The rules and the glossary of a site in one language, the group first. |
| `check(site, language, text, document)` | The rules that a text breaks. |
| `glossary(site, termLanguage, translationLanguage)` | The glossary terms of a language pair, the group first. |
| `checkTranslation(site, sourceLanguage, sourceText, language, text, document)` | The rules that a translation breaks, then each glossary term of the source whose rendering it lacks. |

## Security

- Only published content that passed the workflow reaches the instructions of a model. A check of a
  text also reads the rules of the edit workspace. A node created in the live workspace, such as a
  visitor contribution (`j:originWS=live`), is ignored by both.
- The tool serves a site only to a user with `jContentAccess` on it, and validates the site key and
  the language before any read with a system session.
- Each rule pattern runs with a deadline of 200 ms on a text, and a pattern that overflows the stack
  is skipped, so a costly regex never holds a thread.
