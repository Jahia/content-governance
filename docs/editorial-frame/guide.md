# The editorial frame

Content Governance holds the editorial frame of a brand and its skills, and serves them to the AI
features of Jahia:

- the **voice** of the brand, in Markdown, one text per language;
- the **personas**, the readers the brand writes for, in Markdown, one text per language;
- the **writing rules**: forbidden terms, terms to replace, style patterns and mandatory mentions,
  which code checks with no model;
- the **glossary**: how the brand writes a term in another language, checked on each translation;
- the **voice of each editor**, which applies only where the brand allows it;
- **skills** in the [Agent Skills](https://agentskills.io/specification) format (`SKILL.md`), such
  as an article brief or an SEO review. This module is the only skill registry of Jahia.

Until version 1.12, a separate module named editorial-frame held this code. The Java packages, the
content types, the endpoints and the tool names keep their names.
[`architecture.md`](architecture.md) states how the frame sits in the module.

The drawer and the dashboards of Content Governance do not read the frame yet. The glossary of the
frame is a translation glossary. The Compliance section has its own glossaries of non-authorised
terms, which [`../compliance/glossary-guide.md`](../compliance/glossary-guide.md) describes.

## Where the frame lives

The frame is content of the site. The module defines these types:

| Type | What it holds | Edited by |
|---|---|---|
| `efnt:brandVoice` | The voice, in Markdown (`body`, translated), and its `essentials` (translated). `authorVoiceUnder` lists the paths where an editor's own voice applies. | Brand manager |
| `efnt:persona` | A reader, in Markdown (`body`, translated), and its `essentials` (translated). `isDefault` marks the default reader. | Brand manager, marketing |
| `efnt:writingRule` | `kind` (forbidden, replace, mandatory, style), `pattern`, `matchMode` (term or regex), `replacement`, `ruleLanguage`, `severity` (warning or blocking), `justification` (translated), `wrongExamples` and `rightExamples`, `owner`, `decidedOn`, `validFrom` and `validUntil`. | Legal, brand manager |
| `efnt:glossaryTerm` | One term for one language pair: `term` in `termLanguage`, written `translation` in `translationLanguage`, a `note` and a `definition`. `termForms` and `translationForms` hold the other forms, such as a plural or a feminine form. `status` is `approved`, or `forbidden` for a translation that the brand never writes. A term that the brand never translates has itself as its translation. | Brand manager, translators |
| `efnt:skill` | A skill: `description`, `body` (the Markdown of `SKILL.md`), `activation` (auto, manual, pinned-only), `consumers`, `assistantAction`, and its files as `efnt:skillFile` children. | Brand manager, integrator |
| `efmix:authorVoice` | On a user: `voice`, or `voiceSkill`, the name of a skill that holds the voice. | The editor |

The service finds these nodes by their type anywhere under the site, so no folder is imposed. The
import writes them under one parent folder, `/sites/<site>/contents/ai-assistant`, in the sub-folders `voice`, `personas`, `rules`, `glossary` and `skills`.

Each folder carries a content type restriction (`jmix:contributeMode`, `j:contributeTypes`), so the
create menu and the paste of jContent offer only what belongs there:

| Folder | Accepts |
|---|---|
| `ai-assistant` | The five types of the frame. A new sub-folder inherits this restriction. |
| `voice` | `efnt:brandVoice` |
| `personas` | `efnt:persona` |
| `rules` | `efnt:writingRule`. The import writes the rules of a file here, each with the file as `source`, and a new import of that file replaces them. |
| `glossary` | `efnt:glossaryTerm`. The import writes the terms of a CSV file here, and a new import of that file replaces them. |
| `skills` | `efnt:skill` |

A restriction to folders only is not possible for the parent: jContent intersects it with the
content types a folder holds, finds none, and offers every type. The restriction applies to the
editors of jContent; the JCR itself does not enforce it. `POST /modules/editorial-frame/setup`
creates the folders of a site, or adds the restrictions to existing ones. The frame of `systemsite` is the
frame of the group: every site reads it first. Its rules and glossary terms add up with those of the
site, and a persona of the site replaces a persona of the group with the same name.

### The essentials

Every AI call gets the essentials of each voice and of its persona, whatever its token budget. The
essentials are about 300 tokens, or 1,200 characters:

- The brand manager writes them in the property `essentials`, in the same language as `body`.
  Essentials longer than 1,200 characters keep their first whole lines.
- Without them, a `body` of 1,200 characters or less is its own essentials.
- Without them, a longer `body` gives an outline: each heading with the first sentence of its
  section. A `body` with no heading gives the first sentence of each paragraph.

The budget of the call then holds the writing rules, the glossary terms of the text, and the other
readers. When the frame leaves out part of a text, a note names the files of the generated skill
`brand-frame` that hold it in full. A model with the tool `skill.read` reads them there.

For example, an alternative text gets five lines of the voice. The chat gets the same five lines,
and reads `voices.md` before it writes an article.

### The glossary

A glossary term says how the brand writes a term in another language. For example, "access token"
in `en` is "jeton d'accès" in `fr`. The frame of a language holds the terms whose translation is in
that language:

- `frame.get` and the instructions of a model list only the terms that the text at work holds. A
  call without a text gets no term, and the note of the full texts names `glossary.csv`.
- `frame.check` with `sourceText` and `sourceLocale` checks a translation: where the source holds a
  term, the translation must hold its rendering. A miss comes with kind `glossary`, and the rendering
  in `missing`. The check counts no occurrence.
- A term and its rendering match in any of their forms. With the forms "access tokens" and
  "jetons d'accès", the translation of "Create access tokens" as "Créez des jetons d'accès" passes.
  An apostrophe matches a straight or a curly one.
- A forbidden term names a translation that the brand never writes, such as "jeton
  d'authentification" for "access token". Where the source holds the term, a translation that holds
  the forbidden one comes with kind `glossary`, the text found in `match`, and the approved rendering
  in `replacement`. The forms of a term add up over its approved and forbidden entries.
- The generated skill `brand-frame` holds them in `glossary.csv`.

`fr` and `fr_FR` are the same language for the glossary.

### The examples and the dates of a rule

A writing rule can carry texts that break it, `wrongExamples`, and texts that keep it,
`rightExamples`. The frame gives the model the first two of each, after the justification of the
rule. `owner` and `decidedOn` record who decided the rule, and on which day.

A rule applies from its `validFrom` day to its `validUntil` day, both included, in the time zone of
the server. An empty date sets no limit. Out of its dates, a rule is not part of the frame: the
checks, the instructions of the model and the generated skill leave it out. For example, a ban on
competitor names valid until 2026-12-31 stops on 2027-01-01 with no change in jContent.

Every consumer reads the **live** workspace: a change reaches the AI features only after its
publication, through the workflow of the site.

## What reads the frame

| Consumer | How |
|---|---|
| ai-assistant | The frame goes into the instructions of each turn, after the rules of the assistant. A message `/skill-name ...` runs a skill. A skill of activation `auto` is listed to the model, which reads it with `skill.read`. The approval dialog lists each rule that a proposed value breaks, and each mandatory term that a whole page lacks: a translated page, or a content item shown as its own page. The persona comes from the picker of the drawer. The editor's voice goes with the message, where the brand allows it. |
| MCP clients (Claude, Copilot) | Four read-only tools: `frame.get`, `frame.check`, `skill.list` and `skill.read`. The module adds them only when the module mcp-server is installed. `frame.get` takes the text at work, such as the source of a translation, for its glossary terms. The frame is also served as a generated skill, `brand-frame`, with `voices.md`, `personas.md`, `rules.csv` and `glossary.csv`, in the default language of the site. |
| Other modules | The OSGi service `org.jahia.pm.modules.editorialframe.api.EditorialFrameService`. |

genai-connector does not change: it transports the instructions that a consumer builds.

## Skills

This module is the only skill registry of Jahia. A skill is a `SKILL.md` in the
[Agent Skills](https://agentskills.io/specification) format, with its files. It comes from one of
three places:

| Place | Scope | How |
|---|---|---|
| A node `efnt:skill` of the site | `site` | Created in jContent or by the import. |
| A node `efnt:skill` of `systemsite` | `group` | Every site reads it. |
| A folder of a module | `module` | The manifest header `Jahia-Skills` names the folder, such as `Jahia-Skills: skills`. Each sub-folder with a `SKILL.md` is a skill. |

On a name clash, a skill of the site hides the skill of the group, which hides the skill of a module.
Between two modules, the module that started first keeps the name. This module ships `article-brief`
and `seo-review`.

### The keys that Jahia reads

The Agent Skills keys keep their meaning: `name`, `description`, `license`, `compatibility` and
`allowed-tools`. Jahia reads these keys of `metadata` too:

| Key | Meaning | Default |
|---|---|---|
| `jahia-activation` | `auto`: the model may choose the skill. `manual`: the editor names it. `pinned-only`: only code runs it. | `manual` |
| `jahia-consumers` | Who uses the skill, separated by commas: `chat`, `review`, `ckeditor`, `translation`, `mcp`. | `chat,mcp` |
| `jahia-action` | The action of the assistant that runs the skill, such as `find` or `edit`. | `find` |
| `jahia-label` | The name that a user interface shows, 60 characters at most. | The name |
| `jahia-scope` | One sentence on the work that the skill covers, for the input guard of the assistant, 300 characters at most. | The description, on one line |
| `jahia-in-scope` | Up to 5 messages that the skill covers. | None |
| `jahia-out-of-scope` | Up to 5 messages that look close but that the skill does not cover. | None |

`allowed-tools` lists the Jahia tools that the skill runs with, separated by spaces, such as
`content.get content.update`. A tool of another agent, such as `Read`, is left out with a warning.
With no Jahia tool, the skill runs with the tools of its action.

An example of `jahia-in-scope` or `jahia-out-of-scope` is a message, or a map with `message`,
`note` (why the message goes to this skill, 120 characters at most) and `attached-images` (0 to
10). These two keys hold YAML lists: the Agent Skills format expects text values in `metadata`, and
an agent outside Jahia ignores these keys. A value over a limit is cut or left out, and the log of
the module, or the report of the import, names it.

```markdown
---
name: forms
description: Create or change a form of the site. Use when an editor asks for a form.
allowed-tools: page.outline content.get content.create
metadata:
  jahia-label: Forms
  jahia-action: create
  jahia-scope: create or change a form of the site.
  jahia-in-scope:
    - Add a contact form to the Contact page.
    - message: Put this image above the form.
      attached-images: 1
      note: an attached image in a form
  jahia-out-of-scope:
    - Write me a survey about pizza.
---
Create a form with content.create, as a child of the area that the user names.
```

On an `efnt:skill` node, `allowedTools` holds `allowed-tools`, and `skillMetadata` holds each key of
`metadata` as `key=value`, with a list as JSON. The import writes both.

### From a skill declared for ai-assistant

ai-assistant reads its own skill declarations, JSON files under `META-INF/ai-assistant/skills`, until
it reads its skills from this module. A declaration becomes a `SKILL.md` like this:

| Declaration of ai-assistant | `SKILL.md` |
|---|---|
| `id` | `name`, and the name of the folder |
| `label` | `metadata.jahia-label` |
| `scope` | `metadata.jahia-scope` |
| `inScope`, `outOfScope` | `metadata.jahia-in-scope`, `metadata.jahia-out-of-scope`. `attachedImages` becomes `attached-images`. |
| `tools` | `allowed-tools`, separated by spaces |
| `guidance` | The body |
| (none) | `description`, which the Agent Skills format requires: what the skill does and when to use it |

## The service

The OSGi service `EditorialFrameService` gives the frame and the skills to the other modules:

| Method | Gives |
|---|---|
| `getFrame(site, language)` | The voices, personas, rules and glossary of a site in one language, the group first. |
| `buildContext(request)` | The text of the frame for a model: the essentials, then what the token budget holds. |
| `check(site, language, text, document)` | The rules that a text breaks. |
| `authorVoice(site, language, user, path)` | The voice of an editor, where the brand allows it. |
| `listSkills(site, consumer)` | The skills of a site for a consumer, after the overrides: name, description, label, coverage, tools, activation, action. |
| `listModuleSkills(consumer)` | The skills of every active module, whatever the site, with their bodies. For a module that declares a skill for every site, as ai-assistant does with its built-in skills. |
| `readSkill(site, name)` | One skill of a site with its body and the list of its files. |
| `readSkillFile(site, name, path)` | One file of a skill. |
| `glossary(site, termLanguage, translationLanguage)` | The glossary terms of a language pair, the group first. |
| `checkTranslation(site, sourceLanguage, sourceText, language, text, document)` | The rules that a translation breaks, then each glossary term of the source whose rendering it lacks. |

## Endpoints

| Endpoint | What it does |
|---|---|
| `POST /modules/editorial-frame/import?site=<key>&...` | Imports a zip of skills as drafts. Needs `siteAdministrationAccess` on the site and the body type `application/zip`. |
| `POST /modules/editorial-frame/setup?site=<key>` | Creates `contents/ai-assistant` and its four sub-folders when missing, and sets the type each one accepts. Needs `siteAdministrationAccess` and the body type `application/json` (an empty `{}`). |
| `GET /modules/editorial-frame/voice` | Reads the voice of the current user. |
| `POST /modules/editorial-frame/voice` | Writes the voice of the current user: `{"voice": "...", "voiceSkill": "..."}`. |

### Parameters of the import

| Parameter | Meaning |
|---|---|
| `site` | The site key. Required. |
| `language` | The language of the voice, the personas and the justifications. Default: the default language of the site. |
| `skills` | Folders whose skills are imported, separated by commas, such as `skills/content,skills/seo`. Default: every folder. |
| `voice` | The folder of the skill whose body becomes the voice. |
| `personas`, `personasHeading`, `defaultPersona` | The folder of the skill that holds the personas, the heading of the section whose sub-headings are the personas, and the title of the default one. |
| `rules`, `rulesSeverity` | YAML files of rules, separated by commas, and the severity of their forbidden terms. |
| `glossary` | CSV files of glossary terms, separated by commas. See below. |
| `activation`, `action`, `consumers` | The defaults of the imported skills. A key `jahia-activation`, `jahia-action` or `jahia-consumers` in a skill wins. |
| `ref` | The commit of the source, kept on each skill. |
| `dryRun=true` | Reports what the import would write, and writes nothing. |

The import never runs code. It keeps text files only (`md`, `txt`, `csv`, `json`, `yaml`), leaves
out `scripts/`, refuses a path with `..`, and refuses a file that holds a zero-width or a
bidirectional character. It warns about an HTML comment, a long base64 block and a phrase that tells
a model to ignore its instructions. A `SKILL.md` inside another skill, such as a sub-agent, is a file
of that skill. The files that a skill names in `depends_on` are copied as `deps/<path>`.

A YAML file of rules can hold these keys:

| Key | Becomes |
|---|---|
| `forbidden_terms` | A forbidden term, for every language. |
| `forbidden_terms_<lang>` | A term to replace, when the item has `term` and `replacement`, for that language. |
| `unsafe_patterns` | A forbidden regex, ignoring case. `risk: unsafe` is blocking, any other risk a warning. |
| `sensitive_topics.<topic>.avoid` | A forbidden phrase, as a warning that names the phrases of `prefer`. |
| `rules` | A full rule: `kind`, `pattern`, `match`, `replacement`, `language`, `severity`, `justification`, `wrong_examples`, `right_examples`, `owner`, `decided_on`, `valid_from`, `valid_until`. A date is `YYYY-MM-DD`. |
| `rewrite_examples` | `weak` is a wrong example and `safer` a right example of each rule of the file that `weak` breaks. |
| `counter_examples` | The `text` of `bad` is a wrong example and the `text` of `good` a right example of each rule of the file that `bad` breaks. The reasons are left out. |

An example that breaks no rule of its file is left out with a warning. A rule takes 5 wrong examples
from these keys at most.

A CSV file of glossary terms names its columns in its first row: language codes, and the optional
`note`, `definition` and `status`. The first language is the language of the terms. Each other
language gives one term per row that fills it. A cell gives the other forms of its term after a `|`.
The status is `approved`, the default, or `forbidden`. This file gives four terms: `en` to `fr` and
`en` to `de` for "access token", a forbidden `fr` translation of it, and `en` to `fr` for "Jahia",
which stays as it is.

```csv
en,fr,de,definition,status,note
access token|access tokens,jeton d'accès|jetons d'accès,Zugriffstoken,A secret that lets a program call an API.,,
access token,jeton d'authentification|jetons d'authentification,,,forbidden,another concept
Jahia,Jahia,,,,a brand name
```

## Security

- The frame and the skills go into the system instructions of a model, so only published content
  that passed the workflow reaches them. A node created in the live workspace, such as a visitor
  contribution (`j:originWS=live`), is ignored.
- The MCP tools serve a site only to a user with `jContentAccess` on it, and validate the site key
  and the language before any read with a system session.
- Each rule pattern runs with a deadline of 200 ms on a text, and a pattern that overflows the stack
  is skipped, so a costly regex never holds a thread.
- The text of a skill is wrapped in `<skill_content>`; any spelling of that tag inside a skill is
  neutralized.
- A skill never runs code and never runs under the publish action of the assistant.
