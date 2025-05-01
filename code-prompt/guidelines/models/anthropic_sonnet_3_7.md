# Anthropic Sonnet 3.7 Prompting Guidelines

These guidelines provide comprehensive instructions on how to craft effective prompts for Anthropic Sonnet 3.7. Use these best practices to structure your inputs, manage context, and improve the overall quality of responses.

## Table of Contents
- [Overview](#overview)
- [Prompt Structure](#prompt-structure)
- [Clarity and Specificity](#clarity-and-specificity)
- [Context and Background](#context-and-background)
- [Formatting in Markdown](#formatting-in-markdown)
- [Handling Tone and Style](#handling-tone-and-style)
- [Iterative Refinement](#iterative-refinement)
- [Safety, Ethics, and Limitations](#safety-ethics-and-limitations)
- [Examples](#examples)
- [Troubleshooting and FAQs](#troubleshooting-and-faqs)

## Overview
Anthropic Sonnet 3.7 is a hybrid reasoning model designed to generate high-quality, human-like responses. Clear, context-rich, and well-structured prompts are essential for obtaining effective outputs.

## Prompt Structure

### 1. System Instructions
- **Role Assignment:** Clearly define the assistant's role.  
  *Example:*  
  `You are a subject matter expert in climate science.`

- **Task Definition:** Specify what you want the model to do.  
  *Example:*  
  `Summarize the latest research on renewable energy sources.`

### 2. User Instructions
- **Input Context:** Provide any necessary background or context.  
  *Example:*  
  `Based on the following article excerpt, provide a summary...`

- **Task Constraints:** Include specific formatting or style requirements.  
  *Example:*  
  `Answer in bullet points.`

### 3. Additional Directives
- Clarify any ambiguous terms.
- Provide examples if applicable.
- Set boundaries to avoid off-topic or irrelevant responses.

## Clarity and Specificity
- **Be Direct:** Use precise language and avoid vagueness.
- **Step-by-Step Instructions:** Break down complex tasks into clear, sequential steps.
- **Define Terms:** Explain any specialized or context-specific terminology.

## Context and Background
- **Provide Necessary Background:** Include all relevant information so the model can generate a complete response.
- **Multi-turn Conversations:** Summarize previous dialogue or include key context from earlier turns.
- **Keep Focus:** Limit the prompt to a single task or topic to avoid mixed contexts.

## Formatting in Markdown
When incorporating Markdown into your prompts, consider the following:
- **Headers:** Use hash symbols (e.g., `#`, `##`, `###`) for headers.
- **Lists:** Use ordered (`1.`, `2.`) or unordered (`-`, `*`) lists for clear structure.
- **Code Blocks:** Use triple backticks (```) for code or example blocks.
- **Emphasis:** Use `**bold**` or `*italic*` to highlight important terms.

*Example:*
```markdown
### Task: Summarize the following text
- **Input:** A passage about renewable energy.
- **Output:** A concise summary in bullet points.
```

## Handling Tone and Style
- **Specify Tone:** Indicate if the tone should be formal, conversational, technical, etc.
- **Maintain Consistency:** Request that the model maintain a consistent style throughout the response.
- **Audience Awareness:** Tailor the language and complexity according to the target audience.

*Example:*  
`Explain the theory of relativity in simple terms suitable for high school students.`

## Iterative Refinement
- **Test and Tweak:** Experiment with different prompt variations and adjust based on the model’s outputs.
- **Feedback Loops:** Use feedback from the responses to refine and improve your prompt over multiple iterations.
- **Error Correction:** If the model’s output is off-topic or ambiguous, add more specific instructions or constraints.

## Safety, Ethics, and Limitations
- **Avoid Sensitive Content:** Instruct the model to steer clear of harmful, biased, or explicit topics.
- **Acknowledge Limitations:** Understand and communicate that the model has limitations in reasoning and factual accuracy.
- **Promote Ethical Use:** Ensure that prompts encourage respectful and ethical responses.

*Example:*  
`Ensure your answer is respectful and avoids controversial language.`

## Examples

### Example 1: Summarization Task
```markdown
# Task: Summarize an Article
**Role:** You are a knowledgeable summarizer.
**Input:** [Insert article excerpt here]
**Instruction:** Provide a concise summary in three bullet points.
```

### Example 2: Creative Storytelling
```markdown
# Task: Generate a Short Story
**Role:** You are a creative writer.
**Instruction:** Write a short story in a whimsical tone that includes elements of fantasy and humor.
```

### Example 3: Technical Explanation
```markdown
# Task: Explain a Scientific Concept
**Role:** You are an expert in physics.
**Input:** Explain the theory of relativity.
**Instruction:** Provide a simple explanation suitable for high school students using analogies and examples.
```

## Troubleshooting and FAQs

### Q: Why is the output not matching my expectations?
**A:** Revisit your instructions and ensure that your prompt is unambiguous. Provide additional context or more specific constraints if needed.

### Q: How do I handle multi-turn conversations?
**A:** Summarize the previous context or include the key points from earlier turns to maintain continuity.

### Q: What if the output contains irrelevant information?
**A:** Tighten your instructions by specifying exactly what should be included or omitted. Use directives like “only include...” or “exclude any mention of...”.

## Final Tips
- **Keep It Simple:** For complex tasks, consider breaking them down into simpler sub-tasks.
- **Review and Revise:** Continuously refine your prompt based on the model’s responses.
- **Document Effective Prompts:** Maintain a record of effective prompt structures for future reference.

Happy prompting!
