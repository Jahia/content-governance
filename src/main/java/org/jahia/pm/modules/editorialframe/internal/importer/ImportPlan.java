/*
 * Copyright (C) 2026 - present Jahia Solutions Group SA. Licensed under the MIT License.
 */
package org.jahia.pm.modules.editorialframe.internal.importer;

import org.jahia.pm.modules.editorialframe.api.GlossaryTerm;
import org.jahia.pm.modules.editorialframe.api.WritingRule;
import org.jahia.pm.modules.editorialframe.internal.frame.FrameReader;
import org.jahia.pm.modules.editorialframe.internal.frame.RuleChecker;
import org.jahia.pm.modules.editorialframe.internal.frame.Texts;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillDocument;
import org.jahia.pm.modules.editorialframe.internal.skills.SkillPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What an import writes, computed from the text files of an archive with no repository access:
 * the skills, and the parts of the frame that the options map (a voice, personas, rules).
 */
public final class ImportPlan {

    static final int MAX_FILES_PER_SKILL = 512;
    static final int MAX_DEPENDENCY_CHARS = 600_000;
    static final int MAX_RULES = 2000;
    static final int MAX_TERMS = 5000;
    static final int MAX_TERM_CHARS = 200;
    /** The wrong examples that the examples of a file give to one rule. */
    static final int MAX_EXAMPLES_PER_RULE = 5;
    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(ignore|disregard|forget)\\s+(all\\s+)?(the\\s+)?(previous|prior|above)\\s+(instructions|rules|prompts?)");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=]{300,}");

    /** The options of an import, from the query of the request. */
    public static final class Options {
        public String language;
        /** The folders whose skills are imported, such as skills/content. Empty: every folder. */
        public List<String> skillRoots = new ArrayList<>();
        /** The folder of the skill whose body becomes the voice of the brand. */
        public String voice;
        /** The folder of the skill whose sections become personas. */
        public String personas;
        /** The heading of the section that holds one persona per sub-heading. */
        public String personasHeading = "persona";
        /** The title of the default persona. Empty: the first one. */
        public String defaultPersona;
        /** YAML files of rules. */
        public List<String> rules = new ArrayList<>();
        public String rulesSeverity = "warning";
        /** CSV files of glossary terms. */
        public List<String> glossary = new ArrayList<>();
        public String activation = "manual";
        public String action = "find";
        public List<String> consumers = List.of("chat", "mcp");
    }

    /** A skill to write. */
    public static final class SkillItem {
        public final SkillDocument document;
        public final String sourcePath;
        public final Map<String, String> files;
        private final Options options;

        SkillItem(SkillDocument document, String sourcePath, Map<String, String> files, Options options) {
            this.document = document;
            this.sourcePath = sourcePath;
            this.files = files;
            this.options = options;
        }

        /** The activation of the options, which a key jahia-activation of the skill replaces. */
        public String activation() {
            return options.activation;
        }

        public List<String> consumers() {
            return options.consumers;
        }

        public String action() {
            return options.action;
        }
    }

    /** A voice or a persona to write. */
    public static final class TextItem {
        public final String name;
        public final String title;
        public final String body;
        public final boolean isDefault;

        TextItem(String name, String title, String body, boolean isDefault) {
            this.name = name;
            this.title = title;
            this.body = body;
            this.isDefault = isDefault;
        }
    }

    /** A rule to write, grouped by the file it comes from. */
    public static final class RuleItem {
        public final String source;
        public final WritingRule rule;

        RuleItem(String source, WritingRule rule) {
            this.source = source;
            this.rule = rule;
        }
    }

    /** A glossary term to write, grouped by the file it comes from. */
    public static final class TermItem {
        public final String source;
        public final GlossaryTerm term;

        TermItem(String source, GlossaryTerm term) {
            this.source = source;
            this.term = term;
        }
    }

    public final List<SkillItem> skills = new ArrayList<>();
    public final List<TermItem> glossary = new ArrayList<>();
    public final List<TextItem> personas = new ArrayList<>();
    public final List<RuleItem> rules = new ArrayList<>();
    public TextItem voice;
    public final List<String> warnings = new ArrayList<>();
    public final List<String> skipped = new ArrayList<>();

    public static ImportPlan of(Map<String, String> files, Options options) {
        ImportPlan plan = new ImportPlan();
        String voiceFolder = folder(options.voice);
        String personasFolder = folder(options.personas);
        if (voiceFolder != null) {
            plan.readVoice(files, voiceFolder);
        }
        if (personasFolder != null) {
            plan.readPersonas(files, personasFolder, options);
        }
        for (String rulesFile : options.rules) {
            plan.readRules(files, rulesFile.trim(), options);
        }
        for (String glossaryFile : options.glossary) {
            plan.readGlossary(files, glossaryFile.trim());
        }
        plan.readSkills(files, options, voiceFolder, personasFolder);
        return plan;
    }

    private void readVoice(Map<String, String> files, String folder) {
        SkillDocument document = document(files, folder);
        if (document == null || !safe(folder, document.body)) {
            return;
        }
        voice = new TextItem(document.name, title(document.body, document.name), document.body, false);
    }

    private void readPersonas(Map<String, String> files, String folder, Options options) {
        SkillDocument document = document(files, folder);
        if (document == null) {
            return;
        }
        String[] lines = document.body.split("\n");
        boolean inSection = false;
        String title = null;
        StringBuilder body = new StringBuilder();
        List<String[]> found = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (title != null) {
                    found.add(new String[]{title, body.toString().strip()});
                    title = null;
                }
                inSection = line.toLowerCase(Locale.ROOT).contains(options.personasHeading.toLowerCase(Locale.ROOT));
                continue;
            }
            if (inSection && line.startsWith("### ")) {
                if (title != null) {
                    found.add(new String[]{title, body.toString().strip()});
                }
                title = line.substring(4).strip();
                body.setLength(0);
                continue;
            }
            if (inSection && title != null) {
                body.append(line).append('\n');
            }
        }
        if (title != null) {
            found.add(new String[]{title, body.toString().strip()});
        }
        if (found.isEmpty()) {
            warnings.add(folder + ": no section whose heading holds '" + options.personasHeading + "', so no persona");
            return;
        }
        String wantedDefault = options.defaultPersona == null ? found.get(0)[0] : options.defaultPersona;
        for (String[] persona : found) {
            if (!safe(folder + "#" + persona[0], persona[1])) {
                continue;
            }
            personas.add(new TextItem(slug(persona[0]), persona[0], persona[1], persona[0].equalsIgnoreCase(wantedDefault)));
        }
    }

    private void readRules(Map<String, String> files, String path, Options options) {
        String text = files.get(path);
        if (text == null) {
            warnings.add(path + ": no such file in the archive, so no rule");
            return;
        }
        Object loaded;
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setMaxAliasesForCollections(10);
            loaded = new Yaml(new SafeConstructor(loaderOptions)).load(text);
        } catch (YAMLException e) {
            warnings.add(path + ": not valid YAML, so no rule");
            return;
        }
        if (!(loaded instanceof Map)) {
            warnings.add(path + ": not a YAML map, so no rule");
            return;
        }
        WritingRule.Severity severity = WritingRule.Severity.parse(options.rulesSeverity);
        List<String> unmapped = new ArrayList<>();
        List<String[]> examples = new ArrayList<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) loaded).entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.equals("forbidden_terms") || key.startsWith("forbidden_terms_")) {
                String language = key.equals("forbidden_terms") ? null : key.substring("forbidden_terms_".length());
                if (!(entry.getValue() instanceof Collection)) {
                    continue;
                }
                for (Object item : (Collection<?>) entry.getValue()) {
                    addTermRule(path, key, item, language, severity);
                }
            } else if (key.equals("rules") && entry.getValue() instanceof Collection) {
                for (Object item : (Collection<?>) entry.getValue()) {
                    addFullRule(path, item);
                }
            } else if (key.equals("unsafe_patterns") && entry.getValue() instanceof Collection) {
                for (Object item : (Collection<?>) entry.getValue()) {
                    addUnsafePattern(path, item);
                }
            } else if (key.equals("sensitive_topics") && entry.getValue() instanceof Map) {
                for (Map.Entry<?, ?> topic : ((Map<?, ?>) entry.getValue()).entrySet()) {
                    addSensitiveTopic(path, String.valueOf(topic.getKey()), topic.getValue());
                }
            } else if ((key.equals("rewrite_examples") || key.equals("counter_examples")) && entry.getValue() instanceof Collection) {
                for (Object item : (Collection<?>) entry.getValue()) {
                    String[] example = example(item);
                    if (example != null) {
                        examples.add(example);
                    }
                }
            } else {
                unmapped.add(key);
            }
        }
        if (!unmapped.isEmpty()) {
            warnings.add(path + ": keys with no rule mapping, kept out: " + String.join(", ", unmapped));
        }
        if (rules.size() > MAX_RULES) {
            warnings.add(path + ": more than " + MAX_RULES + " rules, the rest is left out");
            rules.subList(MAX_RULES, rules.size()).clear();
        }
        addExamples(path, examples);
    }

    /**
     * A wrong and a right text, the right one possibly {@code null}: {@code weak} and {@code safer}
     * of a rewrite example, or the {@code text} of {@code bad} and of {@code good} of a counter
     * example. The reasons of a counter example are left out: the justification of the rule says why.
     */
    private static String[] example(Object item) {
        if (!(item instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) item;
        String wrong = exampleText(map.containsKey("weak") ? map.get("weak") : map.get("bad"));
        String right = exampleText(map.containsKey("safer") ? map.get("safer") : map.get("good"));
        return wrong == null ? null : new String[]{wrong, right};
    }

    private static String exampleText(Object value) {
        String text = stringOf(value instanceof Map ? ((Map<?, ?>) value).get("text") : value);
        return text == null || text.isBlank() ? null : text.strip();
    }

    /**
     * Gives each example of a file to each rule of that file that its wrong text breaks. For
     * example, "Jahia is the leading DXP" goes to the rule of the pattern "leading DXP". An example
     * that breaks no rule of the file shows no rule, so it is left out with a warning.
     */
    private void addExamples(String path, List<String[]> examples) {
        for (String[] example : examples) {
            boolean shown = false;
            for (int i = 0; i < rules.size(); i++) {
                RuleItem item = rules.get(i);
                WritingRule rule = item.rule;
                if (!item.source.equals(path) || RuleChecker.check(List.of(rule), rule.language(), example[0], false).isEmpty()) {
                    continue;
                }
                shown = true;
                if (rule.wrongExamples().size() >= MAX_EXAMPLES_PER_RULE || rule.wrongExamples().contains(example[0])) {
                    continue;
                }
                List<String> wrong = new ArrayList<>(rule.wrongExamples());
                wrong.add(example[0]);
                List<String> right = new ArrayList<>(rule.rightExamples());
                if (example[1] != null && !right.contains(example[1])) {
                    right.add(example[1]);
                }
                rules.set(i, new RuleItem(path, rule.withExamples(wrong, right)));
            }
            if (!shown) {
                warnings.add(path + ": the wrong example \"" + example[0].substring(0, Math.min(60, example[0].length()))
                        + "\" breaks no rule of the file, so it is left out");
            }
        }
    }

    private void addTermRule(String path, String key, Object item, String language, WritingRule.Severity severity) {
        String term;
        String replacement = null;
        if (item instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) item;
            term = stringOf(map.get("term"));
            replacement = stringOf(map.get("replacement"));
        } else {
            term = stringOf(item);
        }
        if (term == null || term.isBlank()) {
            return;
        }
        WritingRule.Kind kind = replacement == null || replacement.isBlank() ? WritingRule.Kind.FORBIDDEN : WritingRule.Kind.REPLACE;
        // A forbidden single word becomes a stem, "seamless*", which also catches "seamlessly": the
        // validator of the marketing repository matches a forbidden term as a part of the text.
        String pattern = term.trim();
        if (kind == WritingRule.Kind.FORBIDDEN && !pattern.contains(" ") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        String justification = kind == WritingRule.Kind.REPLACE
                ? "The term that the market uses (" + path + ")."
                : "A term that the brand never uses (" + path + ").";
        rules.add(new RuleItem(path, new WritingRule(null, kind, pattern, WritingRule.MatchMode.TERM, replacement,
                language, severity, justification, "site")));
    }

    /**
     * A pattern of the claim rules of the marketing repository: {@code regex}, {@code risk},
     * {@code reason} and {@code rewrite_hint}. An unsafe claim is blocking, a claim that needs a proof
     * is a warning. The patterns ignore case, as the validators of that repository read them.
     */
    private void addUnsafePattern(String path, Object item) {
        if (!(item instanceof Map)) {
            return;
        }
        Map<?, ?> map = (Map<?, ?>) item;
        String regex = stringOf(map.get("regex"));
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            java.util.regex.Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            warnings.add(path + ": the pattern " + regex + " does not compile in Java, so it is kept out");
            return;
        }
        String risk = stringOf(map.get("risk"));
        String reason = stringOf(map.get("reason"));
        String hint = stringOf(map.get("rewrite_hint"));
        String justification = (reason == null ? "" : reason) + (hint == null ? "" : (reason == null ? "" : ". ") + hint)
                + (risk == null ? "" : " (" + risk + ")");
        rules.add(new RuleItem(path, new WritingRule(null, WritingRule.Kind.FORBIDDEN, "(?i)" + regex,
                WritingRule.MatchMode.REGEX, null, null,
                "unsafe".equals(risk) ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                justification.strip(), "site")));
    }

    /** A sensitive topic: each phrase of {@code avoid} is a warning, which names the phrases to prefer. */
    private void addSensitiveTopic(String path, String topic, Object value) {
        if (!(value instanceof Map)) {
            return;
        }
        Object avoid = ((Map<?, ?>) value).get("avoid");
        Object prefer = ((Map<?, ?>) value).get("prefer");
        if (!(avoid instanceof Collection)) {
            return;
        }
        String preferred = prefer instanceof Collection ? SkillDocument.string(prefer).replace(",", ", ") : null;
        for (Object phrase : (Collection<?>) avoid) {
            String term = stringOf(phrase);
            if (term != null && !term.isBlank()) {
                rules.add(new RuleItem(path, new WritingRule(null, WritingRule.Kind.FORBIDDEN, term.trim(),
                        WritingRule.MatchMode.TERM, null, null, WritingRule.Severity.WARNING,
                        "Sensitive topic " + topic.replace('_', ' ') + (preferred == null ? "" : ". Prefer: " + preferred),
                        "site")));
            }
        }
    }

    /**
     * Reads a CSV file of glossary terms. The first row names the columns: language codes, and the
     * optional {@code note}, {@code definition} and {@code status}. The first language is the
     * language of the terms, and each other language gives one term per row that fills it. For
     * example, the header {@code en,fr,de,note} and the row {@code access token,jeton d'accès,Zugriffstoken,}
     * give two terms, from en to fr and from en to de.
     *
     * <p>A cell of a language may give the other forms of its term after a {@code |}, such as
     * {@code jeton d'accès|jetons d'accès}. The status is {@code approved}, the default, or
     * {@code forbidden}: the row then gives translations that the brand never writes.</p>
     */
    void readGlossary(Map<String, String> files, String path) {
        String text = files.get(path);
        if (text == null) {
            warnings.add(path + ": no such file in the archive, so no glossary term");
            return;
        }
        if (!safe(path, text)) {
            return;
        }
        List<List<String>> rows = Csv.read(text);
        if (rows.isEmpty()) {
            warnings.add(path + ": an empty file, so no glossary term");
            return;
        }
        List<String> header = new ArrayList<>();
        int note = -1;
        int definition = -1;
        int status = -1;
        for (String column : rows.get(0)) {
            String name = column.trim();
            if (note < 0 && "note".equalsIgnoreCase(name)) {
                note = header.size();
            } else if (definition < 0 && "definition".equalsIgnoreCase(name)) {
                definition = header.size();
            } else if (status < 0 && "status".equalsIgnoreCase(name)) {
                status = header.size();
            }
            header.add(name);
        }
        List<Integer> languages = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (i == note || i == definition || i == status) {
                continue;
            }
            if (!FrameReader.LANGUAGE.matcher(header.get(i)).matches()) {
                warnings.add(path + ": the column '" + rows.get(0).get(i)
                        + "' is neither a language code nor note, definition or status, so it is left out");
                continue;
            }
            languages.add(i);
        }
        if (languages.size() < 2) {
            warnings.add(path + ": the first row needs two language codes at least, such as en,fr, so no glossary term");
            return;
        }
        int from = languages.get(0);
        for (List<String> row : rows.subList(1, rows.size())) {
            List<String> termForms = forms(cell(row, from));
            if (termForms.isEmpty()) {
                continue;
            }
            String term = termForms.get(0);
            GlossaryTerm.Status termStatus;
            try {
                termStatus = GlossaryTerm.Status.parse(cell(row, status));
            } catch (IllegalArgumentException e) {
                warnings.add(path + ": the status '" + cell(row, status) + "' of the term '" + shortened(term)
                        + "' is neither approved nor forbidden, so the term is left out");
                continue;
            }
            for (int to : languages.subList(1, languages.size())) {
                List<String> translationForms = forms(cell(row, to));
                if (translationForms.isEmpty()) {
                    continue;
                }
                if (tooLong(termForms) || tooLong(translationForms)) {
                    warnings.add(path + ": the term '" + shortened(term) + "' is longer than " + MAX_TERM_CHARS
                            + " characters, so it is left out");
                    continue;
                }
                if (glossary.size() == MAX_TERMS) {
                    warnings.add(path + ": more than " + MAX_TERMS + " glossary terms, the rest is left out");
                    return;
                }
                glossary.add(new TermItem(path, new GlossaryTerm(null, term, header.get(from),
                        termForms.subList(1, termForms.size()), translationForms.get(0), header.get(to),
                        translationForms.subList(1, translationForms.size()), cell(row, definition), cell(row, note),
                        termStatus, "site")));
            }
        }
    }

    /** The forms of a cell of a glossary, separated by {@code |}: the term first. */
    static List<String> forms(String cell) {
        List<String> forms = new ArrayList<>();
        if (cell != null) {
            for (String form : cell.split("\\|")) {
                String trimmed = form.trim();
                if (!trimmed.isEmpty() && !forms.contains(trimmed)) {
                    forms.add(trimmed);
                }
            }
        }
        return forms;
    }

    private static boolean tooLong(List<String> forms) {
        return forms.stream().anyMatch(form -> form.length() > MAX_TERM_CHARS);
    }

    private static String shortened(String term) {
        return term.substring(0, Math.min(40, term.length()));
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return null;
        }
        String value = row.get(index).replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? null : value;
    }

    private void addFullRule(String path, Object item) {
        if (!(item instanceof Map)) {
            return;
        }
        Map<?, ?> map = (Map<?, ?>) item;
        String pattern = stringOf(map.get("pattern"));
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        if ("glossary".equalsIgnoreCase(stringOf(map.get("kind")))) {
            warnings.add(path + ": a rule of kind glossary is kept out: a glossary comes from a CSV file (" + pattern + ")");
            return;
        }
        if ("regex".equalsIgnoreCase(stringOf(map.get("match")))) {
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException e) {
                warnings.add(path + ": the pattern " + pattern + " does not compile in Java, so it is kept out");
                return;
            }
        }
        try {
            rules.add(new RuleItem(path, new WritingRule(null,
                    WritingRule.Kind.parse(stringOf(map.get("kind"))), pattern,
                    WritingRule.MatchMode.parse(stringOf(map.get("match"))), stringOf(map.get("replacement")),
                    stringOf(map.get("language")), WritingRule.Severity.parse(stringOf(map.get("severity"))),
                    stringOf(map.get("justification")), "site")
                    .withExamples(texts(map.get("wrong_examples")), texts(map.get("right_examples")))
                    .withDecision(stringOf(map.get("owner")), day(map.get("decided_on")))
                    .withValidity(day(map.get("valid_from")), day(map.get("valid_until")))));
        } catch (IllegalArgumentException e) {
            warnings.add(path + ": a rule with an unknown kind, match or severity is kept out (" + pattern + ")");
        } catch (DateTimeParseException e) {
            warnings.add(path + ": a rule with a date that is not YYYY-MM-DD is kept out (" + pattern + ")");
        }
    }

    /** The texts of a YAML list, or of a single text. */
    private static List<String> texts(Object value) {
        List<String> texts = new ArrayList<>();
        for (Object item : value instanceof Collection ? (Collection<?>) value : value == null ? List.of() : List.of(value)) {
            String text = stringOf(item);
            if (text != null && !text.isBlank()) {
                texts.add(text.strip());
            }
        }
        return texts;
    }

    /** A day of a YAML file: YAML reads 2026-12-31 as a timestamp at midnight UTC, and '2026-12-31' as a text. */
    static LocalDate day(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).trim());
    }

    private void readSkills(Map<String, String> files, Options options, String voiceFolder, String personasFolder) {
        TreeSet<String> directories = new TreeSet<>();
        for (String path : files.keySet()) {
            if (path.equals("SKILL.md") || path.endsWith("/SKILL.md")) {
                directories.add(path.substring(0, path.length() - "SKILL.md".length()));
            }
        }
        List<String> roots = new ArrayList<>();
        for (String directory : directories) {
            if (roots.stream().noneMatch(directory::startsWith)) {
                roots.add(directory);
            }
        }
        for (String root : roots) {
            String folder = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
            if (folder.equals(voiceFolder) || folder.equals(personasFolder)) {
                continue;
            }
            if (!options.skillRoots.isEmpty() && options.skillRoots.stream().map(ImportPlan::folder)
                    .noneMatch(prefix -> folder.equals(prefix) || folder.startsWith(prefix + "/"))) {
                continue;
            }
            SkillDocument document = document(files, folder);
            if (document == null || !safe(folder, document.body)) {
                continue;
            }
            Map<String, String> skillFiles = new LinkedHashMap<>();
            for (Map.Entry<String, String> file : files.entrySet()) {
                if (!file.getKey().startsWith(root) || file.getKey().equals(root + "SKILL.md")) {
                    continue;
                }
                String relative = file.getKey().substring(root.length());
                if (!SkillPaths.isKept(relative)) {
                    skipped.add(file.getKey() + ": scripts and binary files are never imported");
                    continue;
                }
                if (safe(file.getKey(), file.getValue())) {
                    skillFiles.put(relative, file.getValue());
                }
            }
            addDependencies(files, document, folder, voiceFolder, personasFolder, skillFiles);
            if (skillFiles.size() > MAX_FILES_PER_SKILL) {
                skipped.add(folder + ": more than " + MAX_FILES_PER_SKILL + " files");
                continue;
            }
            for (String warning : document.warnings) {
                warnings.add(folder + ": " + warning);
            }
            if (skills.stream().anyMatch(other -> other.document.name.equals(document.name))) {
                skipped.add(folder + ": another skill of the archive has the name " + document.name);
                continue;
            }
            skills.add(new SkillItem(document, folder, skillFiles, options));
        }
    }

    /**
     * Copies the files that the skill names in {@code depends_on}, a key that the marketing
     * repository uses and the Agent Skills format does not define, as {@code deps/<path>}. The
     * folders of the voice and of the personas are left out: the frame already carries them.
     */
    private void addDependencies(Map<String, String> files, SkillDocument document, String folder, String voiceFolder,
                                 String personasFolder, Map<String, String> skillFiles) {
        long total = 0;
        for (String dependency : document.metadataList("depends_on")) {
            String path = dependency.replaceAll("/+$", "");
            if (path.equals(voiceFolder) || path.equals(personasFolder) || !SkillPaths.isSafeRelative(path)) {
                continue;
            }
            String local = folder.isEmpty() ? path : folder + "/" + path;
            String source = files.containsKey(local) ? local
                    : files.containsKey(path) ? path : files.containsKey(path + "/SKILL.md") ? path + "/SKILL.md" : null;
            if (source != null && source.equals(local) && skillFiles.containsKey(path)) {
                // A file of the skill itself, already among its files.
                continue;
            }
            if (source == null) {
                warnings.add(folder + ": the dependency " + dependency + " is not in the archive");
                continue;
            }
            String text = files.get(source);
            total += text.length();
            if (total > MAX_DEPENDENCY_CHARS) {
                warnings.add(folder + ": the dependencies pass " + MAX_DEPENDENCY_CHARS + " characters, the rest is left out");
                return;
            }
            if (SkillPaths.isKept(source) && safe(source, text)) {
                skillFiles.put("deps/" + source, text);
            }
        }
    }

    private SkillDocument document(Map<String, String> files, String folder) {
        String text = files.get(folder.isEmpty() ? "SKILL.md" : folder + "/SKILL.md");
        if (text == null) {
            warnings.add(folder + ": no SKILL.md");
            return null;
        }
        String folderName = folder.contains("/") ? folder.substring(folder.lastIndexOf('/') + 1) : folder;
        try {
            return SkillDocument.parse(text, folderName);
        } catch (IllegalArgumentException e) {
            skipped.add(folder + ": " + e.getMessage());
            return null;
        }
    }

    /** The scan of a text before it can reach a prompt: invisible characters refuse it, the rest warns. */
    private boolean safe(String where, String text) {
        if (Texts.hasInvisibleCharacters(text)) {
            skipped.add(where + ": invisible characters (zero width or bidirectional) can hide instructions");
            return false;
        }
        if (INJECTION.matcher(text).find()) {
            warnings.add(where + ": a phrase that tells a model to ignore its instructions");
        }
        if (text.contains("<!--")) {
            warnings.add(where + ": an HTML comment, which a reader of the rendered text does not see");
        }
        if (BASE64.matcher(text).find()) {
            warnings.add(where + ": a long base64 block");
        }
        return true;
    }

    static String folder(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.trim().replaceAll("/SKILL\\.md$", "").replaceAll("^/+|/+$", "");
    }

    static String title(String body, String fallback) {
        for (String line : body.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).strip();
            }
        }
        return fallback;
    }

    static String slug(String title) {
        String slug = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 48) {
            slug = slug.substring(0, 48).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "persona" : slug;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
