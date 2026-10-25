# Phase 3b.1 shared contract (#44)

## Entry and catalogue
Create FunctionCatalogue from the existing CodeGenerationInput or Phase2bResult.
getInput retains the original accepted tree, SymbolTable and NumericMode. No resolver or
analyser is run by the catalogue. Tree/table must remain unchanged during generation.

getFunctions is an immutable deterministic declaration/preorder list (parent before child).
requireFunction(Symbol,useSite) uses object identity; requireGeneratedName(name,useSite)
uses the existing generated name only. Neither performs source-name lookup. Missing/null,
foreign or non-function bindings produce contextual CodeGenerationException diagnostics.

Function implements InliningContract.FunctionTemplate and retains the original function
Symbol, F_TYPE declaration, body P, body ALGO, ordered parameter/local Symbols, direct
nested F_TYPE declarations and optional numeric return TERM. Lists are immutable views of
metadata, not cloned ASTs. Void has no return term. Nested same-spelled names are distinct.
Templates here are metadata only: code-mine emission/expansion belongs to #46/#49/#50.

## Interfaces for teammates
InliningContract contains:
- FunctionTemplate: original semantic metadata listed above.
- LexicalEnvironment: storageFor(originalSymbol,useSite), getParent(). Own formal/local
  slots belong to the current instance; free variables delegate to the enclosing lexical
  instance. Never make a new unrelated slot for an outer captured symbol.
- TargetNames: newVariable(), newLabel(). Implement with one whole-output deterministic
  allocator, reserving all Phase 2a names and sharing a collision domain with Phase 3a.
- InlineInstance: template, lexical environment, stable labelFor(templateLabel), result.
  Every expansion, including copied nested expansions, gets fresh definitions AND jump targets.
- ReturnResult: Optional value slot; empty for void, typed fresh storage for numeric functions.

These are contracts, not implementations of later issues. #48 supplies instance/remapping
machinery. Do not mutate Symbols or AST bindings when creating an instance. Mapping keys
are Symbol identities, not names. Do not rename via substring replacement in emitted text.

## Argument/return policy (proposed team decisions)
Walk the INPUT list collecting each direct TERM in source order; nested arithmetic terms
are not extra arguments. Evaluate actuals left-to-right exactly once into fresh typed capture
slots. Finish ALL captures before assigning any formal. Then bind formal=capture in order,
execute expanded body, evaluate numeric return TERM into the instance return slot, and copy
callerContinuation=returnSlot. Void has no result copy. Locals/formals/temps/return slots
are distinct per expansion; referenced outer storage is deliberately shared with its actual
lexical owner. No new runtime stack or runtime recursion is introduced. Do not invent user
variable initialization semantics or redo semantic visibility/type analysis.

requireArity(function,actualCount,callNode) checks missing/extra actuals and reports expected
and actual counts with node context. This is a backend safety policy, not a newly asserted
Phase 2b specification rule; call expansion must invoke it before emission.

## Unresolved tutor/lecture clarifications
1. On p.3, the sheet identifies pN as the captured actual and aN as the formal, but prints
   `pN = aN`. Our implementation uses `aN = pN` so the actual argument is passed into the
   formal. Ask whether the printed assignment is a typo and confirm that `aN = pN` is
   intended.


## Commands
Compile production and tests as usual, then:

    java -ea -cp out/classes spl.codegen.FunctionCatalogueTest

The future complete target (NOT today's Phase 3a fragment) can be built using:

    g++ -std=c++11 output/program.cpp -o output/program.exe
    ./output/program.exe

Or substitute clang++ for g++. CXX is a single compiler executable for the existing test
harness. No compiler is installed by this issue. Native function execution is not claimed:
this issue implements the handoff/catalogue only, not full executable code generation.
