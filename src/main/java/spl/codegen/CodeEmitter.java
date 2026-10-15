package spl.codegen;

import java.util.*;
import spl.semantic.NumericMode;

/** One function/fragment emission session. Declarations are hoisted before all body jumps. */
public final class CodeEmitter {
    private final NumericMode mode;
    private final Set<String> reserved;
    private final Set<String> used = new HashSet<>();
    private final Set<String> labels = new HashSet<>();
    private final Set<String> defined = new HashSet<>();
    private final Set<String> referenced = new HashSet<>();
    private final LinkedHashSet<String> temporaries = new LinkedHashSet<>();
    private final StringBuilder body = new StringBuilder();
    private int temporaryCounter, labelCounter;
    public CodeEmitter(CodeGenerationInput input) {
        if (input == null) throw new CodeGenerationException("missing input");
        mode = input.getNumericMode(); reserved = new HashSet<>(input.getReservedNames()); reset();
    }
    /** Begin an independent run; previous generated names must not be reused afterward. */
    public void reset() {
        used.clear(); used.addAll(reserved); labels.clear(); defined.clear(); referenced.clear();
        temporaries.clear(); body.setLength(0); temporaryCounter = 0; labelCounter = 0;
    }
    public String newTemporary() {
        String name;
        do { name = "spl_tmp_" + temporaryCounter++; } while (!used.add(name));
        temporaries.add(name); return name;
    }
    public String newLabel() {
        String name;
        do { name = "spl_label_" + labelCounter++; } while (!used.add(name));
        labels.add(name); return name;
    }
    /** Exactly one target statement, without its semicolon; quote-aware semicolon checking. */
    public void emitStatement(String statement) {
        if (statement == null || statement.isBlank()) throw new CodeGenerationException("empty statement");
        boolean quoted = false, escaped = false;
        for (char c : statement.toCharArray()) {
            if (c == '\n' || c == '\r') throw new CodeGenerationException("statement must be one line");
            if (!quoted && c == ';') throw new CodeGenerationException("supply statement without semicolon");
            if (c == '"' && !escaped) quoted = !quoted;
            if (c == '\\' && !escaped) escaped = true; else escaped = false;
        }
        if (quoted) throw new CodeGenerationException("unterminated quoted statement");
        body.append(statement.strip()).append(";\n");
    }
    public void emitComment(String text) {
        if (text == null) throw new CodeGenerationException("missing comment");
        body.append("/* ").append(text.replace("*/", "* /").replace('\r', ' ').replace('\n', ' ')).append(" */\n");
    }
    private void requireLabel(String label) {
        if (!labels.contains(label)) throw new CodeGenerationException("label was not allocated in this run: " + label);
    }
    public void emitLabel(String label) {
        requireLabel(label);
        if (!defined.add(label)) throw new CodeGenerationException("duplicate label: " + label);
        // Empty statement makes an ending label valid C++ too.
        body.append(label).append(": ;\n");
    }
    public void emitGoto(String label) {
        requireLabel(label); referenced.add(label); emitStatement("goto " + label);
    }
    public void emitConditionalGoto(String comparison, String label) {
        requireLabel(label);
        if (comparison == null || comparison.isBlank() || comparison.contains(";")
                || comparison.contains("\n") || comparison.contains("\r"))
            throw new CodeGenerationException("invalid comparison");
        // Rejected statements must not leave a phantom jump reference behind.
        emitStatement("if (" + comparison + ") goto " + label);
        referenced.add(label);
    }
    /** Returns a fragment, never a main function or source-symbol declarations. */
    public String render() {
        if (!defined.containsAll(referenced)) throw new CodeGenerationException("undefined jump target");
        StringBuilder output = new StringBuilder();
        for (String name : temporaries) output.append(mode.getTargetType()).append(' ').append(name).append(";\n");
        return output.append(body).toString();
    }
}
