# Phase 3a: algorithm fragment generation

## Run and build (PowerShell from repository root)

```powershell
New-Item -ItemType Directory -Force out/classes | Out-Null
$sources = Get-ChildItem src/main/java, test/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac -d out/classes $sources
# Existing XML-only interface remains supported:
java -cp out/classes spl.Main
java -cp out/classes spl.Main input/arithmetic.spl output/tree.xml
# Third optional positional argument opts into target fragment generation:
java -cp out/classes spl.Main input/arithmetic.spl output/tree.xml output/algorithm.cpp
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

## Output contract / safety
XML is still written in its existing format after successful Phase 2b. The optional
third path receives only the selected top-level P's immediate ALGO translation,
not algorithms inside function declarations. Successful unused function declarations
are allowed, but a CALL reached within the selected ALGO (including nested branches,
loops or terms) is rejected explicitly as deferred to Phase 3b.

The requested target path is checked against source and XML paths (including existing
filesystem aliases). A previous target file is invalidated before compilation begins,
so failed lexing/parsing/semantic analysis/generation cannot leave a stale fragment
looking successful. Output directories are created on successful generation, and a
UTF-8 temporary sibling is moved into place atomically where supported (replace fallback
otherwise). Do not point the target argument at a valuable unrelated file: it is an
output artifact and will be replaced/removed. Directories are never deleted as targets.
Source/XML/target aliases are rejected without deleting their contents.

Expected frontend/semantic/code-generation/file failures produce a phase-specific message
and exit 1; unexpected internal errors exit 2. The success banner is emitted only after
all requested outputs succeed. A valid XML tree may remain if later target generation
fails; XML success is not a claim of target-code success.

## Supported/deferred grammar
Supported: empty ALGO (/* end */), ordered INSTR ; ALGO traversal, numeric/name terms,
add/sub/mul/div/mod/neg, string and numeric print, assignment, nop, comment,
eq/larger/lesser, short-circuit and/or/not, if/then/else translated using gotos,
and pre/post-test while/until. Nested algorithm bodies dispatch through the same generator.
No target else/while/do or logical composition operators implement SPL control flow.

Deferred: production source-variable declarations, functions, function calls/inlining,
program wrapper/includes/main and a standalone executable. The .cpp artifact is a
FRAGMENT, not a complete compilable program. The test wrapper uses C++ because the
spec's green output examples use std::cout despite describing a C-inspired target.

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
All translators share one emitter per fragment. Production source declarations stay deferred.

## Verification and native compiler setup
Phase3aEndToEndTest uses the production generator with no translator overrides. It covers
each grammar family, integer/real modes, comparison and nested Boolean truth tables,
short-circuit cases with unsafe skipped operands, all four loops with zero/one/multiple
iterations, nested flow, determinism, label/temporary structure, and CLI file/error behavior.
Existing frontend/semantic and individual-translator tests remain part of the full suite.

Native tests wrap fragments with test-only source-variable declarations and a C++ main,
compare stdout with explicit expected outcomes, and apply process timeouts. The wrapper
is NOT production Phase 3b implementation. Files are created in temporary directories.
With no compiler available, Java/static/CLI checks run but native behavior is explicitly
reported SKIP, never as passed. Native behavior remains unverified until those tests run.
Set CXX to a single compiler executable (not a command with flags), or put g++/clang++
on PATH. For example, if installed:

```powershell
$env:CXX = 'C:/msys64/ucrt64/bin/g++.exe'
java -ea -cp out/classes spl.codegen.Phase3aEndToEndTest
```

Look for PASS native Phase 3a execution. A started compiler that rejects the generated
code is a test failure, not a skip. No compiler installation is performed automatically.
