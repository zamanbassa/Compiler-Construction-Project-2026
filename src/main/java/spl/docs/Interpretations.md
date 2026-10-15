# Phase 3a.5 — Boolean Code Generation

## 1. Interpretation of the Lecture Hints

The Boolean code generator uses control-flow translation instead of computing and storing Boolean values.

The method `emitBool(boolNode, trueLabel, falseLabel)` receives two explicit jump destinations:

- `trueLabel`: destination when the Boolean expression evaluates to true.
- `falseLabel`: destination when the Boolean expression evaluates to false.

Each translated Boolean expression transfers control to exactly one of these destinations.

### Comparisons

The operators `eq`, `larger`, and `lesser` are mapped to `==`, `>`, and `<`, respectively.

Both numeric operands are evaluated using `emitTerm()` and stored in temporary variables.

The generated code then emits a conditional jump to `trueLabel`, followed by an unconditional jump to `falseLabel`.

### Negation (`not`)

Negation is implemented by swapping the destinations:

```java
emitBool(operand, falseLabel, trueLabel);
```

This reverses the Boolean outcome without generating a target-language `!` expression.

### Conjunction (`and`)

Conjunction uses an intermediate label.

```text
emitBool(left, nextLabel, falseLabel)
nextLabel:
emitBool(right, trueLabel, falseLabel)
```

If the left operand is false, execution jumps directly to `falseLabel`. The right operand is evaluated only when the left operand is true.

### Disjunction (`or`)

Disjunction also uses an intermediate label.

```text
emitBool(left, trueLabel, nextLabel)
nextLabel:
emitBool(right, trueLabel, falseLabel)
```

If the left operand is true, execution jumps directly to `trueLabel`. The right operand is evaluated only when the left operand is false.

## 2. Truth-Table Validation

| A | B | and(A,B) | or(A,B) | not(A) |
|---|---|---|---|---|
| False | False | False | False | True |
| False | True | False | True | True |
| True | False | False | True | False |
| True | True | True | True | False |

## 3. Short-Circuit Validation

| Expression | Right Operand Evaluated? |
|---|---|
| and(False, B) | No |
| and(True, B) | Yes |
| or(True, B) | No |
| or(False, B) | Yes |

Short-circuit behaviour is implemented through conditional and unconditional jumps, rather than C++ logical operators.

## 4. Nested Boolean Expressions

Nested Boolean expressions are handled recursively using the same label-based translation contract.

For example:

```text
or(and(A, B), C)
```

Evaluation proceeds as follows:

1. Evaluate A.
2. If A is true, evaluate B; otherwise skip B.
3. If `and(A,B)` is true, jump to the overall true destination.
4. Otherwise, evaluate C.
5. Jump to the destination determined by C.

This preserves the intended Boolean semantics while avoiding unnecessary operand evaluation.

## 5. Testing

The `CodeGenerationFoundationTest` suite verifies:

- Comparison operators and their true/false outcomes.
- All four truth-table combinations for `and` and `or`.
- Double negation.
- Nested Boolean expressions.
- Short-circuit execution paths.
- Generated C++ compilation and execution.

The tests were run in WSL Ubuntu using `g++`.

## 6. Design Decision

The implementation deliberately does not translate SPL Boolean expressions into C++ `&&`, `||`, or `!` expressions.

Instead, it uses labels and jumps, consistent with the chosen interpretation of the lecture's control-flow code-generation hints.