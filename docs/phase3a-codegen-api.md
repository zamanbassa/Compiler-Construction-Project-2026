# Phase 3 code generation APIs

## Run and build (PowerShell from repository root)

```powershell
New-Item -ItemType Directory -Force out/classes | Out-Null
$sources = Get-ChildItem src/main/java, test/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac -d out/classes $sources
# Existing XML-only interface remains supported:
java -cp out/classes spl.Main
java -cp out/classes spl.Main input/arithmetic.spl output/tree.xml
# Third optional positional argument opts into complete C++ translation-unit generation:
java -cp out/classes spl.Main input/arithmetic.spl output/tree.xml output/program.cpp
java -ea -cp out/classes spl.codegen.Phase3aEndToEndTest
```

Run all Java test entry points:

```powershell
$testRoot = (Resolve-Path test/java).Path
Get-ChildItem test/java -Recurse -Filter *Test.java | ForEach-Object {
    $class = $_.FullName.Substring($testRoot.Length + 1) -replace '[\\/]', '.' -replace '\.java$', ''
    java -ea -cp out/classes $class
    if ($LASTEXITCODE -ne 0) { throw "Failed: $class" }
}
```

## CLI output contract / safety
XML is written in its existing format after successful Phase 2b. With zero, one, or two
CLI arguments, `Main` retains XML-only behavior. The third optional path requests a
complete C++ translation unit, including function templates in the non-executable code
mine, source-variable declarations, expanded calls, and the `main` wrapper.

The input and XML output paths are checked for aliases in every CLI mode, including
XML-only mode. When C++ output is requested, its path must also differ from both input
and XML paths.

The requested target path is checked against source and XML paths (including existing
filesystem aliases). A previous target file is invalidated before compilation begins,
so failed lexing/parsing/semantic analysis/generation cannot leave stale generated source
looking successful. Output directories are created on successful generation, and a
UTF-8 temporary sibling is moved into place atomically where supported (replace fallback
otherwise). Do not point the target argument at a valuable unrelated file: it is an
output artifact and will be replaced/removed. Directories are never deleted as targets.
Source/XML/target aliases are rejected without deleting their contents.

Expected frontend/semantic/code-generation/file failures produce a phase-specific message
and exit 1; unexpected internal errors exit 2. The success banner is emitted only after
all requested outputs succeed. A valid XML tree may remain if later target generation
fails; XML success is not a claim of target-code success. The CLI emits source code but
does not automatically invoke a native compiler.

The third CLI path is not a fragment mode. Callers that need only the selected top-level
`P`'s immediate `ALGO` translation can use the Java fragment API below. That API excludes
function bodies and rejects a call reached in the selected algorithm.

## Supported/deferred grammar
The Phase 3a fragment API supports: empty ALGO (/* end */), ordered INSTR ; ALGO traversal, numeric/name terms,
add/sub/mul/div/mod/neg, string and numeric print, assignment, nop, comment,
eq/larger/lesser, short-circuit and/or/not, if/then/else translated using gotos,
and pre/post-test while/until. Nested algorithm bodies dispatch through the same generator.
No target else/while/do or logical composition operators implement SPL control flow.

The full-program generator additionally supports production source-variable declarations,
functions, and function-call inlining. Its `.cpp` output is a complete translation unit.
The fragment API remains useful for testing or embedding the Phase 3a algorithm translator;
its fragment is not a standalone executable. Generated output uses C++ because the output
uses `std::cout`, despite the specification's C-inspired terminology.

## Phase 2b handoff and numeric policy

```java
Phase2aResult names = new DeclarationResolver().resolveWithResult(parsedTree);
Phase2bResult checked = new TypeAnalyzer(names.getSymbolTable()).analyzeWithResult(names);
CodeGenerationInput input = new CodeGenerationInput(checked);
TreeNode algo = TargetFileOutput.mainAlgorithm(input);
String fragment = new AlgorithmCodeGenerator(input).generate(algo);
```

The original annotated tree and symbol table are retained. Backend code does not re-run
name resolution/type analysis; it uses resolved Symbol identities and generated names.
Input validation checks annotations, bindings, numeric-mode compatibility and names.
Do not mutate the tree/table during generation. The direct input constructor is a contract
for already-accepted Phase 2b output, not a replacement semantic analyser.

NumericMode.WHOLE emits int; REAL emits float. Phase 2b rejects mod+div and mod+decimal.
For accepted programs, div or a decimal numeric literal selects REAL; mod selects WHOLE.
Otherwise WHOLE is the documented default project policy, not an additional PDF rule.
SemanticType.NUMERIC remains separate from representation. A string containing a dot
is not a decimal numeric literal.

## Shared API and emitter
CodeGenerator: emitAlgo(algo), emitInstruction(instr), emitTerm(term,resultVariable),
emitBool(bool,trueLabel,falseLabel). emitTerm assigns to caller-declared storage;
emitBool transfers to exactly one destination (no implicit fall-through); not swaps labels.
Generate resets state and returns the completed selected fragment. LoopCodeGenerator
remains a compatibility subclass; the base generator now supports loops directly too.

CodeEmitter allocates spl_tmp_N/spl_label_N avoiding all symbol names across scopes.
newTemporary automatically registers an uninitialized declaration hoisted before all
instructions. Assignments retain execution order. Translators must assign values before
reading them. emitStatement expects no terminator and adds semicolon plus newline;
emitLabel emits label: ; so an ending label is valid; emitComment safely formats comments.
Jump methods require allocated labels; render rejects undefined targets. reset starts an
independent deterministic run. Rejected conditional statements do not register references.
All translators share one emitter per fragment. Source declarations and function expansion
are outside the fragment API and are handled by the full-program generator.

## Verification and native compiler setup
`Phase3aEndToEndTest` covers the fragment translator and the full-program CLI path. It uses
the production generator with no translator overrides and covers
each grammar family, integer/real modes, comparison and nested Boolean truth tables,
short-circuit cases with unsafe skipped operands, all four loops with zero/one/multiple
iterations, nested flow, determinism, label/temporary structure, CLI file/error behavior,
and native compilation/execution of a CLI-generated full program. Inlining and call behavior
are also covered by `InlineCallExecutionTest` and `InlineCallRegressionTest`. Existing
frontend/semantic and individual-translator tests remain part of the full suite.

Fragment native tests wrap fragments with test-only source-variable declarations and a C++
`main`; this wrapper is only for testing the fragment API. Full-program native tests compile
and execute the source produced by the CLI, without a test-only wrapper. Tests compare
stdout with explicit expected outcomes and apply process timeouts. Temporary test files are
removed. With no compiler available, Java/static/CLI checks still run, but native behavior
is explicitly reported SKIP, never as passed; native behavior remains unverified until a
compiler-enabled test run succeeds.
Set CXX to a single compiler executable (not a command with flags), or put g++/clang++
on PATH. For example, if installed:

```powershell
$env:CXX = 'C:/msys64/ucrt64/bin/g++.exe'
java -ea -cp out/classes spl.codegen.Phase3aEndToEndTest
```

Look for PASS native Phase 3a execution and PASS CLI-generated full-program compile/run.
A started compiler that rejects generated code is a test failure, not a skip. No compiler
installation is performed automatically.
