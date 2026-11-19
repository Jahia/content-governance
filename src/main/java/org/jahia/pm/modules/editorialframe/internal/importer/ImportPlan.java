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

import java.text.Normalizer;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    private static final String MORE_THAN = ": more than ";
    private static final Pattern INJECTION = Pattern.compile(
            "(?i)(ignore|disregard|forget)\\s+(all\\s+)?(the\\s+)?(previous|prior|above)\\s+(instructions|rules|prompts?)");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=]{300,}");

    /** The options of an import, from the query of the request. */
    public static final class Options {
        private String language;
        /** The folders whose skills are imported, such as skills/content. Empty: every folder. */
        private List<String> skillRoots = new ArrayList<>();
        /** The folder of the skill whose body becomes the voice of the brand. */
        private String voice;
        /** The folder of the skill whose sections become personas. */
        private String personas;
        /** The heading of the section that holds one persona per sub-heading. */
        private String personasHeading = "persona";
        /** The title of the default persona. Empty: the first one. */
        private String defaultPersona;
        /** YAML files of rules. */
        private List<String> rules = new ArrayList<>();
        private String rulesSeverity = "warning";
        /** CSV files of glossary terms. */
        private List<String> glossary = new ArrayList<>();
        private String activation = "manual";
        private String action = "find";
        private List<String> consumers = List.of("chat", "mcp");

        public String language() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public List<String> skillRoots() {
            return skillRoots;
        }

        public void setSkillRoots(List<String> skillRoots) {
            this.skillRoots = skillRoots;
        }

        public String voice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public String personas() {
            return personas;
        }

        public void setPersonas(String personas) {
            this.personas = personas;
        }

        public String personasHeading() {
            return personasHeading;
        }

        public void setPersonasHeading(String personasHeading) {
            this.personasHeading = personasHeading;
        }

        public String defaultPersona() {
            return defaultPersona;
        }

        public void setDefaultPersona(String defaultPersona) {
            this.defaultPersona = defaultPersona;
        }

        public List<String> rules() {
            return rules;
        }

        public void setRules(List<String> rules) {
            this.rules = rules;
        }

        public String rulesSeverity() {
            return rulesSeverity;
        }

        public void setRulesSeverity(String rulesSeverity) {
            this.rulesSeverity = rulesSeverity;
        }

        public List<String> glossary() {
            return glossary;
        }

        public void setGlossary(List<String> glossary) {
            this.glossary = glossary;
        }

        public String activation() {
            return activation;
        }

        public void setActivation(String activation) {
            this.activation = activation;
        }

        public String action() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public List<String> consumers() {
            return consumers;
        }

        public void setConsumers(List<String> consumers) {
            this.consumers = consumers;
        }
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
            return options.activation();
        }

        public List<String> consumers() {
            return options.consumers();
        }

        public String action() {
            return options.action();
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

    /**
     * The columns of a CSV file of glossary terms: the names of its first row, and the index of each
     * optional column, or -1.
     */
    private record GlossaryColumns(List<String> header, int note, int definition, int status) {

        static GlossaryColumns of(List<String> firstRow) {
            List<String> header = new ArrayList<>();
            int note = -1;
            int definition = -1;
            int status = -1;
            for (String column : firstRow) {
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
            return new GlossaryColumns(header, note, definition, status);
        }

        boolean isOptional(int index) {
            return index == note || index == definition || index == status;
        }
    }

    private final List<SkillItem> skills = new ArrayList<>();
    private final List<TermItem> glossary = new ArrayList<>();
    private final List<TextItem> personas = new ArrayList<>();
    private final List<RuleItem> rules = new ArrayList<>();
    private TextItem voice;
    private final List<String> warnings = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();

    public static ImportPlan of(Map<String, String> files, Options options) {
        ImportPlan plan = new ImportPlan();
        String voiceFolder = folder(options.voice());
        String personasFolder = folder(options.personas());
        if (voiceFolder != null) {
            plan.readVoice(files, voiceFolder);
        }
        if (personasFolder != null) {
            plan.readPersonas(files, personasFolder, options);
        }
        for (String rulesFile : options.rules()) {
            plan.readRules(files, rulesFile.trim(), options);
        }
        for (String glossaryFile : options.glossary()) {
            plan.readGlossary(files, glossaryFile.trim());
        }
        plan.readSkills(files, options, voiceFolder, personasFolder);
        return plan;
    }

    public List<SkillItem> skills() {
        return skills;
    }

    public List<TermItem> glossary() {
        return glossary;
    }

    public List<TextItem> personas() {
        return personas;
    }

    public List<RuleItem> rules() {
        return rules;
    }

    /** The voice to write, or {@code null}. */
    public TextItem voice() {
        return voice;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** The files and skills left out, with the reason. A caller may add the entries that the archive left out. */
    public List<String> skipped() {
        return skipped;
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
        List<String[]> found = personaSections(document.body, options.personasHeading());
        if (found.isEmpty()) {
            warnings.add(folder + ": no section whose heading holds '" + options.personasHeading() + "', so no persona");
            return;
        }
        String wantedDefault = options.defaultPersona() == null ? found.get(0)[0] : options.defaultPersona();
        for (String[] persona : found) {
            if (safe(folder + "#" + persona[0], persona[1])) {
                personas.add(new TextItem(slug(persona[0]), persona[0], persona[1],
                        persona[0].equalsIgnoreCase(wantedDefault)));
            }
        }
    }

    /**
     * The title and the text of each sub-heading of the sections whose heading holds a text. For
     * example, with the text persona, the section "## Persona framework" and its sub-heading
     * "### CMO" give the persona CMO.
     */
    private static List<String[]> personaSections(String body, String heading) {
        List<String[]> found = new ArrayList<>();
        boolean inSection = false;
        String title = null;
        StringBuilder text = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("## ")) {
                if (title != null) {
                    found.add(new String[]{title, text.toString().strip()});
                    title = null;
                }
                inSection = line.toLowerCase(Locale.ROOT).contains(heading.toLowerCase(Locale.ROOT));
            } else if (inSection && line.startsWith("### ")) {
                if (title != null) {
                    found.add(new String[]{title, text.toString().strip()});
                }
                title = line.substring(4).strip();
                text.setLength(0);
            } else if (inSection && title != null) {
                text.append(line).append('\n');
            }
        }
        if (title != null) {
            found.add(new String[]{title, text.toString().strip()});
        }
        return found;
    }

    private void readRules(Map<String, String> files, String path, Options options) {
        Optional<Map<?, ?>> loaded = rulesFile(files, path);
        if (loaded.isEmpty()) {
            return;
        }
        WritingRule.Severity severity = WritingRule.Severity.parse(options.rulesSeverity());
        List<String> unmapped = new ArrayList<>();
        List<String[]> examples = new ArrayList<>();
        for (Map.Entry<?, ?> entry : loaded.get().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!readRuleKey(path, key, entry.getValue(), severity, examples)) {
                unmapped.add(key);
            }
        }
        if (!unmapped.isEmpty()) {
            warnings.add(path + ": keys with no rule mapping, kept out: " + String.join(", ", unmapped));
        }
        if (rules.size() > MAX_RULES) {
            warnings.add(path + MORE_THAN + MAX_RULES + " rules, the rest is left out");
            rules.subList(MAX_RULES, rules.size()).clear();
        }
        addExamples(path, examples);
    }

    /** The YAML map of a file of rules, or nothing, with a warning, for a file that is missing or not a map. */
    private Optional<Map<?, ?>> rulesFile(Map<String, String> files, String path) {
        String text = files.get(path);
        if (text == null) {
            warnings.add(path + ": no such file in the archive, so no rule");
            return Optional.empty();
        }
        Object loaded;
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setMaxAliasesForCollections(10);
            loaded = new Yaml(new SafeConstructor(loaderOptions)).load(text);
        } catch (YAMLException e) {
            warnings.add(path + ": not valid YAML, so no rule");
            return Optional.empty();
        }
        if (!(loaded instanceof Map)) {
            warnings.add(path + ": not a YAML map, so no rule");
            return Optional.empty();
        }
        return Optional.of((Map<?, ?>) loaded);
    }

    /** Reads the value of one key of a file of rules. False for a key that maps to no rule. */
    private boolean readRuleKey(String path, String key, Object value, WritingRule.Severity severity,
                                List<String[]> examples) {
        if (key.equals("forbidden_terms") || key.startsWith("forbidden_terms_")) {
            addTermRules(path, key, value, severity);
            return true;
        }
        if (key.equals("rules") && value instanceof Collection<?> items) {
            items.forEach(item -> addFullRule(path, item));
            return true;
        }
        if (key.equals("unsafe_patterns") && value instanceof Collection<?> items) {
            items.forEach(item -> addUnsafePattern(path, item));
            return true;
        }
        if (key.equals("sensitive_topics") && value instanceof Map<?, ?> topics) {
            topics.forEach((topic, topicValue) -> addSensitiveTopic(path, String.valueOf(topic), topicValue));
            return true;
        }
        if ((key.equals("rewrite_examples") || key.equals("counter_examples")) && value instanceof Collection<?> items) {
            items.stream().map(ImportPlan::example).filter(Objects::nonNull).forEach(examples::add);
            return true;
        }
        return false;
    }

    /** The terms of a key {@code forbidden_terms}, or {@code forbidden_terms_<language>} for one language. */
    private void addTermRules(String path, String key, Object value, WritingRule.Severity severity) {
        String language = key.equals("forbidden_terms") ? null : key.substring("forbidden_terms_".length());
        if (value instanceof Collection<?> items) {
            for (Object item : items) {
                addTermRule(path, key, item, language, severity);
            }
        }
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
        String text = stringOf(value instanceof Map<?, ?> map ? map.get("text") : value);
        return text == null || text.isBlank() ? null : text.strip();
    }

    /**
     * Gives each example of a file to each rule of that file that its wrong text breaks. For
     * example, "Jahia is the leading DXP" goes to the rule of the pattern "leading DXP". An example
     * that breaks no rule of the file shows no rule, so it is left out with a warning.
     */
    private void addExamples(String path, List<String[]> examples) {
        for (String[] example : examples) {
            if (!giveExample(path, example)) {
                warnings.add(path + ": the wrong example \"" + example[0].substring(0, Math.min(60, example[0].length()))
                        + "\" breaks no rule of the file, so it is left out");
            }
        }
    }

    /**
     * Gives an example to each rule of the file that its wrong text breaks, unless the rule holds it
     * or holds its share of examples already. False when the example breaks no rule of the file.
     */
    private boolean giveExample(String path, String[] example) {
        boolean shown = false;
        for (int i = 0; i < rules.size(); i++) {
            RuleItem item = rules.get(i);
            WritingRule rule = item.rule;
            if (item.source.equals(path) && !RuleChecker.check(List.of(rule), rule.language(), example[0], false).isEmpty()) {
                shown = true;
                if (rule.wrongExamples().size() < MAX_EXAMPLES_PER_RULE && !rule.wrongExamples().contains(example[0])) {
                    rules.set(i, new RuleItem(path, withExample(rule, example)));
                }
            }
        }
        return shown;
    }

    /** The rule with one more wrong text, and with the right text when it has one that the rule lacks. */
    private static WritingRule withExample(WritingRule rule, String[] example) {
        List<String> wrong = new ArrayList<>(rule.wrongExamples());
        wrong.add(example[0]);
        List<String> right = new ArrayList<>(rule.rightExamples());
        if (example[1] != null && !right.contains(example[1])) {
            right.add(example[1]);
        }
        return rule.withExamples(wrong, right);
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
                language, severity, justification, "site", List.of(), List.of(), null, null, null, null)));
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
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            warnings.add(path + ": the pattern " + regex + " does not compile in Java, so it is kept out");
            return;
        }
        String risk = stringOf(map.get("risk"));
        String reason = stringOf(map.get("reason"));
        String hint = stringOf(map.get("rewrite_hint"));
        String beforeHint = reason == null ? "" : ". ";
        String justification = (reason == null ? "" : reason) + (hint == null ? "" : beforeHint + hint)
                + (risk == null ? "" : " (" + risk + ")");
        rules.add(new RuleItem(path, new WritingRule(null, WritingRule.Kind.FORBIDDEN, "(?i)" + regex,
                WritingRule.MatchMode.REGEX, null, null,
                "unsafe".equals(risk) ? WritingRule.Severity.BLOCKING : WritingRule.Severity.WARNING,
                justification.strip(), "site", List.of(), List.of(), null, null, null, null)));
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
                        "site", List.of(), List.of(), null, null, null, null)));
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
        GlossaryColumns columns = GlossaryColumns.of(rows.get(0));
        List<Integer> languages = languageColumns(path, rows.get(0), columns);
        if (languages.size() < 2) {
            warnings.add(path + ": the first row needs two language codes at least, such as en,fr, so no glossary term");
            return;
        }
        for (List<String> row : rows.subList(1, rows.size())) {
            if (!readGlossaryRow(path, columns, languages, row)) {
                return;
            }
        }
    }

    /** The indexes of the language columns. Each column that is neither a language nor optional gives a warning. */
    private List<Integer> languageColumns(String path, List<String> firstRow, GlossaryColumns columns) {
        List<Integer> languages = new ArrayList<>();
        for (int i = 0; i < columns.header().size(); i++) {
            if (columns.isOptional(i)) {
                continue;
            }
            if (FrameReader.LANGUAGE.matcher(columns.header().get(i)).matches()) {
                languages.add(i);
            } else {
                warnings.add(path + ": the column '" + firstRow.get(i)
                        + "' is neither a language code nor note, definition or status, so it is left out");
            }
        }
        return languages;
    }

    /**
     * Adds the terms of a row of a glossary file, one for each other language that the row fills.
     * False when the glossary holds its limit of terms, so that no other row is read.
     */
    private boolean readGlossaryRow(String path, GlossaryColumns columns, List<Integer> languages, List<String> row) {
        int from = languages.get(0);
        List<String> termForms = forms(cell(row, from));
        if (termForms.isEmpty()) {
            return true;
        }
        String term = termForms.get(0);
        GlossaryTerm.Status termStatus;
        try {
            termStatus = GlossaryTerm.Status.parse(cell(row, columns.status()));
        } catch (IllegalArgumentException e) {
            warnings.add(path + ": the status '" + cell(row, columns.status()) + "' of the term '" + shortened(term)
                    + "' is neither approved nor forbidden, so the term is left out");
            return true;
        }
        for (int to : languages.subList(1, languages.size())) {
            List<String> translationForms = forms(cell(row, to));
            if (translationForms.isEmpty()) {
                continue;
            }
            if (tooLong(termForms) || tooLong(translationForms)) {
                warnings.add(path + ": the term '" + shortened(term) + "' is longer than " + MAX_TERM_CHARS
                        + " characters, so it is left out");
            } else if (glossary.size() == MAX_TERMS) {
                warnings.add(path + MORE_THAN + MAX_TERMS + " glossary terms, the rest is left out");
                return false;
            } else {
                glossary.add(new TermItem(path, new GlossaryTerm(null, term, columns.header().get(from),
                        termForms.subList(1, termForms.size()), translationForms.get(0), columns.header().get(to),
                        translationForms.subList(1, translationForms.size()), cell(row, columns.definition()),
                        cell(row, columns.note()), termStatus, "site")));
            }
        }
        return true;
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
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                warnings.add(path + ": the pattern " + pattern + " does not compile in Java, so it is kept out");
                return;
            }
        }
        try {
            rules.add(new RuleItem(path, new WritingRule(null,
                    WritingRule.Kind.parse(stringOf(map.get("kind"))), pattern,
                    WritingRule.MatchMode.parse(stringOf(map.get("match"))), stringOf(map.get("replacement")),
                    stringOf(map.get("language")), WritingRule.Severity.parse(stringOf(map.get("severity"))),
                    stringOf(map.get("justification")), "site",
                    texts(map.get("wrong_examples")), texts(map.get("right_examples")),
                    stringOf(map.get("owner")), day(map.get("decided_on")),
                    day(map.get("valid_from")), day(map.get("valid_until")))));
        } catch (IllegalArgumentException e) {
            warnings.add(path + ": a rule with an unknown kind, match or severity is kept out (" + pattern + ")");
        } catch (DateTimeParseException e) {
            warnings.add(path + ": a rule with a date that is not YYYY-MM-DD is kept out (" + pattern + ")");
        }
    }

    /** The items of a YAML list, a single value as its one item, and no item for {@code null}. */
    private static Collection<?> items(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return value == null ? List.of() : List.of(value);
    }

    /** The texts of a YAML list, or of a single text. */
    private static List<String> texts(Object value) {
        List<String> texts = new ArrayList<>();
        for (Object item : items(value)) {
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
        for (String root : skillRoots(files.keySet())) {
            String folder = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
            if (isImported(folder, options, voiceFolder, personasFolder)) {
                readSkill(files, options, root, folder, voiceFolder, personasFolder);
            }
        }
    }

    /**
     * The folders that hold a {@code SKILL.md} and lie in no other such folder, in path order, each
     * with its trailing slash. The folder of a {@code SKILL.md} at the root of the archive is empty.
     */
    private static List<String> skillRoots(Set<String> paths) {
        TreeSet<String> directories = new TreeSet<>();
        for (String path : paths) {
            if (path.equals(SkillPaths.SKILL_FILE) || path.endsWith("/" + SkillPaths.SKILL_FILE)) {
                directories.add(path.substring(0, path.length() - SkillPaths.SKILL_FILE.length()));
            }
        }
        List<String> roots = new ArrayList<>();
        for (String directory : directories) {
            if (roots.stream().noneMatch(directory::startsWith)) {
                roots.add(directory);
            }
        }
        return roots;
    }

    /**
     * True for the folder of a skill to import: not the folder of the voice or of the personas, and
     * under a folder of the option {@code skillRoots} when it names any.
     */
    private static boolean isImported(String folder, Options options, String voiceFolder, String personasFolder) {
        if (folder.equals(voiceFolder) || folder.equals(personasFolder)) {
            return false;
        }
        return options.skillRoots().isEmpty() || options.skillRoots().stream().map(ImportPlan::folder)
                .anyMatch(prefix -> folder.equals(prefix) || folder.startsWith(prefix + "/"));
    }

    /** Adds the skill of a folder, with its files and its dependencies, or the reason it is left out. */
    private void readSkill(Map<String, String> files, Options options, String root, String folder, String voiceFolder,
                           String personasFolder) {
        SkillDocument document = document(files, folder);
        if (document == null || !safe(folder, document.body)) {
            return;
        }
        Map<String, String> skillFiles = skillFiles(files, root);
        addDependencies(files, document, folder, voiceFolder, personasFolder, skillFiles);
        if (skillFiles.size() > MAX_FILES_PER_SKILL) {
            skipped.add(folder + MORE_THAN + MAX_FILES_PER_SKILL + " files");
            return;
        }
        for (String warning : document.warnings) {
            warnings.add(folder + ": " + warning);
        }
        if (skills.stream().anyMatch(other -> other.document.name.equals(document.name))) {
            skipped.add(folder + ": another skill of the archive has the name " + document.name);
            return;
        }
        skills.add(new SkillItem(document, folder, skillFiles, options));
    }

    /** The files of a skill folder but its {@code SKILL.md}, by relative path. Scripts and binary files are skipped. */
    private Map<String, String> skillFiles(Map<String, String> files, String root) {
        Map<String, String> skillFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            if (!file.getKey().startsWith(root) || file.getKey().equals(root + SkillPaths.SKILL_FILE)) {
                continue;
            }
            String relative = file.getKey().substring(root.length());
            if (!SkillPaths.isKept(relative)) {
                skipped.add(file.getKey() + ": scripts and binary files are never imported");
            } else if (safe(file.getKey(), file.getValue())) {
                skillFiles.put(relative, file.getValue());
            }
        }
        return skillFiles;
    }

    /** The first of the paths that the archive holds, or {@code null}. */
    private static String firstHeld(Map<String, String> files, String... paths) {
        for (String path : paths) {
            if (files.containsKey(path)) {
                return path;
            }
        }
        return null;
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
            String source = dependencySource(files, dependency, folder, voiceFolder, personasFolder, skillFiles);
            if (source == null) {
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

    /**
     * The path in the archive of a dependency to copy, or {@code null}: for the folder of the voice or
     * of the personas, an unsafe path, a file of the skill itself, and a path that the archive lacks,
     * which gives a warning.
     */
    private String dependencySource(Map<String, String> files, String dependency, String folder, String voiceFolder,
                                    String personasFolder, Map<String, String> skillFiles) {
        String path = dependency.replaceAll("/+$", "");
        if (path.equals(voiceFolder) || path.equals(personasFolder) || !SkillPaths.isSafeRelative(path)) {
            return null;
        }
        String local = folder.isEmpty() ? path : folder + "/" + path;
        String source = firstHeld(files, local, path, path + "/" + SkillPaths.SKILL_FILE);
        if (source == null) {
            warnings.add(folder + ": the dependency " + dependency + " is not in the archive");
            return null;
        }
        // A file of the skill itself is already among its files.
        return source.equals(local) && skillFiles.containsKey(path) ? null : source;
    }

    private SkillDocument document(Map<String, String> files, String folder) {
        String text = files.get(folder.isEmpty() ? SkillPaths.SKILL_FILE : folder + "/" + SkillPaths.SKILL_FILE);
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
        return Texts.trim(path.trim().replaceAll("/SKILL\\.md$", ""), '/');
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
        String slug = Texts.trim(Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"), '-');
        if (slug.length() > 48) {
            // The slug starts with no dash, so the trim removes the dash that the cut may leave at its end.
            slug = Texts.trim(slug.substring(0, 48), '-');
        }
        return slug.isEmpty() ? "persona" : slug;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
