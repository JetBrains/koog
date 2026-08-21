---
name: arithmetic-evaluator
description: Evaluate arithmetic expressions and return numeric results. Use when a user asks an arithmetic question, requests calculation, or provides a math expression involving numbers, parentheses, and arithmetic operators.
---

Evaluate the arithmetic expression the user provides.

1. Extract the arithmetic expression from the user request.
2. If the expression is missing or ambiguous, ask for a clear arithmetic expression.
3. Evaluate it by passing the expression to `scripts/evaluate_arithmetic_exp.py`
4. Return the computed result clearly and concisely.

If script execution fails, explain that clearly and ask the user to provide a valid arithmetic expression.