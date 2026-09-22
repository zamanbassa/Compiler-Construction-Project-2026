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
```

On macOS/Linux, the equivalent commands are:

```text
javac -d out/classes $(find src/main/java -name "*.java")
javac -d out/classes $(find src/main/java test/java -name "*.java")
java -ea -cp out/classes spl.lexer.LexerTest
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
- `Symbol.getType()` starts at `SemanticType.UNKNOWN` for Phase 2b to refine.

Each `Scope` retains its parent, child scopes, direct declarations, and owning `TreeNode`. Duplicate source names in one scope raise `DuplicateDeclarationException`; nested scopes may shadow ancestor declarations.
