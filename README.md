# SPL Compiler

A starter Java project structure for an SPL compiler implementation.

## Structure

- `src/main/java/spl`: compiler source code
- `test/java/spl`: tests for lexer, parser, and tree builder
- `input`: sample SPL input programs
- `output`: generated artifacts (for example XML syntax trees)

## Build and Run

On Windows PowerShell, compile the production sources with:

```powershell
$classes = "out\\classes"
New-Item -ItemType Directory -Force $classes
$sources = Get-ChildItem src\\main\\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d $classes $sources
```

Compile and run the dependency-free lexer tests with assertions enabled:

```powershell
$classes = "out\\classes"
$sources = Get-ChildItem src\\main\\java,test\\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d $classes $sources
java -ea -cp $classes spl.lexer.LexerTest
java -ea -cp $classes spl.parser.ParserTest
java -ea -cp $classes spl.tree.TreeBuilderTest
java -ea -cp $classes spl.semantic.SemanticDataStructureTest
java -ea -cp $classes spl.MainTest
```

The complete Phase 1 and Phase 2a test suite can be run on Windows with:

```powershell
$classes = "out\\classes"
$sources = Get-ChildItem src\\main\\java,test\\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d $classes $sources
@(
  "spl.lexer.LexerTest",
  "spl.parser.ParserTest",
  "spl.tree.TreeBuilderTest",
  "spl.semantic.SemanticDataStructureTest",
  "spl.semantic.DeclarationResolverTest",
  "spl.MainTest"
) | ForEach-Object { java -ea -cp $classes $_ }
```

On macOS/Linux, the equivalent commands are:

```text
javac -d out/classes $(find src/main/java test/java -name "*.java")
for test in \
  spl.lexer.LexerTest \
  spl.parser.ParserTest \
  spl.tree.TreeBuilderTest \
  spl.semantic.SemanticDataStructureTest \
  spl.semantic.DeclarationResolverTest \
  spl.MainTest; do
  java -ea -cp out/classes "$test" || exit 1
done
```

The lexer is available through `spl.lexer.Lexer`:

```java
List<Token> tokens = new Lexer().tokenize(sourceText);
```

The SPL specification requires every token to be followed by a blank. The lexer accepts
ASCII space, carriage return, and line feed as separators, emits an `EOF` token, and throws
`spl.lexer.LexicalException` with line and column information for invalid input.

Keyword and punctuation tokens use the existing generic categories `KEYWORD` and `SYMBOL`;
their exact spelling remains available through `Token.getLexeme()` for the parser.

## Phase 2a semantic symbol table

The `spl.semantic` package provides the scope and declaration model used by the Phase 2a name resolver:

- `SymbolTable.enterScope()` and `exitScope()` manage the lexical scope stack.
- `declare()` creates variable or function symbols with original and generated names.
- `lookupLocal()` searches only the current scope.
- `resolve()` searches the current scope and then its ancestors.
- `DeclarationResolver.resolveWithResult()` returns both the resolved syntax tree and the `SymbolTable` for Phase 2b.
- `Symbol.getType()` starts at `SemanticType.UNKNOWN` for Phase 2b to refine.

Each `Scope` retains its parent, child scopes, direct declarations, and owning `TreeNode`. Duplicate source names in one scope raise `DuplicateDeclarationException`; nested variables may shadow ancestor declarations, but function-body locals may not duplicate parameters. Variable uses search ancestors, never sideways. Function calls resolve only in the current program scope's immediate `F_DECL`; self-recursion, mutual recursion, and calls to ancestor/sibling functions are rejected.

## Running the compiler

`Main` uses `input/sample.spl` and `output/tree.xml` when no arguments are supplied. Custom paths can be provided as:

```text
java -cp out/classes spl.Main <input-file> <output-xml-file>
```

Phase 2a tests can be run on macOS/Linux with:

```text
javac -d out/classes $(find src/main/java test/java -name "*.java")
java -ea -cp out/classes spl.semantic.DeclarationResolverTest
java -ea -cp out/classes spl.MainTest
```

`Main` runs lexing, parsing, Phase 2a name resolution, and Phase 2b type analysis before writing the syntax tree. `Phase2aResult` preserves the resolved tree and symbol table. Expected user-program errors display the failing phase and message and exit with code 1. Unexpected internal errors exit with code 2.

The parser currently uses the generated SLR parse table supplied with the project. This is intentional: it keeps the grammar decision explicit and deterministic while preserving the existing Phase 1 parser behaviour. The XML document remains wrapped in `<tree>` until the tutor confirms whether a different document root is required.

## Phase 2b type model

`SemanticType` contains UNKNOWN, OK, NUMERIC, PROCEDURE and BOOLEAN.
Both `TreeNode` and `Symbol` expose `getType()` and `setType(SemanticType)`;
new instances start at UNKNOWN and null types are rejected. Node types and
symbol types are separate: the analyser must explicitly annotate the node and
update its declaration symbol where the specification requires it.

Phase 2b must consume the existing `Phase2aResult` tree and symbol table.
Use `TreeNode.getResolvedSymbol()` for declaration identity; do not rerun
`DeclarationResolver` or resolve source names again. Type errors should throw
`TypeAnalysisException`, whose diagnostics begin with "Type analysis error".
Its optional node constructor reports a node ID, not a source line number.
`TypeAnalyzer.analyze()` runs the tree crawler and the global mod/div conflict check.
Both mod+div and mod+decimal programs are rejected with the specified conflict
message. `Main` reports numeric conflicts as normal type-checking failures.
Assignment targets and call-name nodes receive their resolved symbol types.
OUTP containing a STRING is OK; no semantic STRING type is required.

After compiling production and test sources, run:

```text
java -ea -cp out/classes spl.semantic.TypeModelTest
```

## Semantic closeout regression tests

After compiling all production and test sources, run:

```text
java -ea -cp out/classes spl.semantic.Phase2CloseoutTest
```

This checks hierarchical function calls, recursion rejection, ancestor-only variable
lookup, name annotations, numeric conflicts, and child-process CLI diagnostics,
exit codes and output creation. `input/sample.spl` supports the no-argument run.
The arithmetic, assignment and loop examples declare their variables before the
first colon; the loop example also initializes its counter.

## Phase 3a code-generation foundation

Issues 3a.1 and 3a.2 provide numeric-mode handoff, validated input, shared translator
interfaces and a deterministic typed emitter. See [the API guide](docs/phase3a-codegen-api.md)
for API details, supported grammar, numeric policy and verification commands. The
instruction translators, complete-program generator and optional target-file output are
implemented.

## Phase 3 program generation and CLI

`Main` preserves the existing XML-only invocations. A third positional path opts into
generation of a **complete C++ translation unit**:

```text
java -cp out/classes spl.Main [input-file] [output-xml-file] [output-cpp-file]
```

For example:

```text
java -cp out/classes spl.Main input/arithmetic.spl output/tree.xml output/program.cpp
```

With zero arguments, the compiler uses `input/sample.spl` and `output/tree.xml`. With one
or two arguments, it writes XML only. With three arguments, it writes the XML and the
complete C++ source, including the non-executable function code mine and the executable
program wrapper. Source, XML, and C++ output paths must be distinct. Target directories
are created as needed, and target output is written through a temporary file.

The CLI emits C++ source; it does **not** invoke a native compiler. To build and run the
generated source, use an installed C++ compiler, for example:

```text
g++ -std=c++11 output/program.cpp -o output/program
./output/program
```

Phase 3a fragment generation remains available through the Java code-generation API for
callers that specifically need an algorithm fragment. It is not the output mode selected
by the third CLI path. See [the API guide](docs/phase3a-codegen-api.md) for details.

After compiling production and test sources, the full-program CLI regression test can be
run with `java -ea -cp out/classes spl.codegen.Phase3aEndToEndTest`. It includes a native
compile/run smoke test when `CXX`, `g++`, or `clang++` is available; if no compiler is
available, native execution is reported as skipped, not passed.
