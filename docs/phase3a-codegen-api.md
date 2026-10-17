# Phase 3a.1 / 3a.2: shared code-generation foundation

## Scope
These issues provide a validated input contract, translator hooks, and emission utilities.
They do not implement arithmetic/instructions/Boolean/branch/loop translation (3a.3–7),
ALGO traversal/file/CLI integration (3a.8), or a production executable wrapper (3b).
An uninstalled translator throws a CodeGenerationException; it never returns misleading empty output.
Main's existing XML pipeline is unchanged.

## Successful Phase 2b handoff

```java
Phase2aResult names = new DeclarationResolver().resolveWithResult(parsedTree);
Phase2bResult checked = new TypeAnalyzer(names.getSymbolTable()).analyzeWithResult(names);
CodeGenerationInput input = new CodeGenerationInput(checked);
```

analyzeWithResult invokes type analysis once and returns the SAME tree/table objects.
The existing analyze(TreeNode) API is preserved for current callers.
The code-generation package never calls DeclarationResolver or TypeAnalyzer.
Use existing node.getResolvedSymbol().getGeneratedName(); do not resolve source names again.
Input validation checks the supplied annotations, binding identity, target-name uniqueness,
and explicit numeric-mode consistency. It does not infer semantic types.
The handoff retains mutable Phase 2 objects: callers must not change them during generation.
A direct CodeGenerationInput(tree, table, mode) constructor is also available to integrations
that already hold successful Phase 2b output. It is a caller contract, not proof against
someone fabricating an OK tree; prefer Phase2bResult.

## Numeric mode contract (team implementation policy)
SemanticType.NUMERIC remains unchanged. NumericMode is separate:
- WHOLE emits int temporary declarations.
- REAL emits float temporary declarations.
Phase 2b rejects mod+div and mod+decimal as before. For successful analyzeWithResult:
- div or any decimal NUM implies REAL.
- mod implies WHOLE.
- Integer-only, unconstrained programs default to WHOLE.
That last default is a documented project policy, not an explicit rule in the PDF.
A caller can explicitly select REAL for an otherwise unconstrained program using the direct
constructor. A mode contradicting mod/div/decimal tokens is rejected.
Strings containing dots are not decimal NUM tokens.

## Shared translation API
CodeGenerator declares:
- emitAlgo(TreeNode algo): append the selected algorithm, not every ALGO in the program.
- emitInstruction(TreeNode instruction): append one INSTR.
- emitTerm(TreeNode term, String resultVariable): assign the numeric result to a caller-declared variable.
- emitBool(TreeNode bool, String trueLabel, String falseLabel): transfer control to EXACTLY
  one destination. No implicit fall-through. For not, swap destinations; compose and/or
  through intermediate labels. This explicit contract makes nested expressions composable.

AlgorithmCodeGenerator provides generate(algo), which validates ALGO membership, rejects
CALL in that selected subtree, resets the emitter, invokes emitAlgo, and renders a fragment.
Override hooks for later issues, calling protected require(node, production) to check inputs.
The base hooks deliberately throw 'translator not installed'. A declaration node passed to
this entry is rejected; function and declaration translation are Phase 3b work.
CALL and TERM containing CALL are explicitly deferred to Phase 3b. Unused function bodies
outside the selected ALGO are not translated or rejected simply for containing a call.

## Emitter
One CodeEmitter per fragment/function:
- newTemporary(): fresh typed temporary; declaration automatically registered.
- newLabel(): fresh numbered label.
- emitStatement(text): one statement WITHOUT its semicolon. Adds semicolon + LF newline.
- emitComment(text): block comment; escapes closing-comment sequences and line breaks.
- emitLabel(label): emits label: ; followed by newline. The empty statement permits an end label.
- emitGoto(label), emitConditionalGoto(comparison, label): emit jumps to allocated labels.
- render(): all temporary declarations first, then the body; rejects undefined jump targets.
- reset(): clears names/counters/body/declarations/jumps and restores original reserved names.

Names are spl_tmp_N and spl_label_N, avoiding all generated symbol names across all scopes
and each other. No static allocation state; independent runs are deterministic.
Do not allocate separate emitters inside translators or labels/temporaries may collide.

Declarations are uninitialized and hoisted to the fragment beginning. Assignments stay in
execution order. Thus a forward/backward goto cannot skip a local declaration with an
initializer. Later translators must assign each temporary on every path before reading it.
The emitter does not declare source variables or emit main/includes. Do not manually insert
local declarations through emitStatement; use newTemporary for generated numeric storage.
Raw statement/comparison text is compiler-internal output, not an API for arbitrary user code.

## Tests and commands (PowerShell, repository root)

```powershell
New-Item -ItemType Directory -Force out/classes | Out-Null
$sources = Get-ChildItem src/main/java, test/java -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
javac -d out/classes $sources
java -ea -cp out/classes spl.codegen.CodeGenerationFoundationTest
```

Tests cover original object identity, numeric modes, missing inputs/bindings, foreign tables,
CALL rejection, unsupported entry points, hundreds of colliding names/labels, reset/determinism,
formatting, duplicate/undefined labels and declaration hoisting.
A test-only adapter exercises the entry without implementing the ALGO traversal issue.
An optional native C++ test compiles and executes manually emitted nested jump flow, expecting 3.
It uses CXX (a single executable path), g++, or clang++; if none starts, it explicitly reports SKIP.
The wrapper is C++ because the spec uses std::cout despite calling its target C.
Temporary compilation/execution files are outside the repository and cleaned up.
