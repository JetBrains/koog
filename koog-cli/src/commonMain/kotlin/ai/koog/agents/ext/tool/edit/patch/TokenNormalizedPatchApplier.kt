package ai.koog.agents.ext.tool.edit.patch

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun applyTokenNormalizedPatch(content: String, patch: FilePatch): PatchApplyResult {
    if (patch.isDelete || patch.isReplace) {
        val contentTokens = TokenList(tokenize(content))
        val originalTokens = TokenList(tokenize(patch.original))

        val match = contentTokens.find(originalTokens) { fst, snd ->
            if (fst.isWhitespace && snd.isWhitespace) {
                true
            } else {
                fst.content.equals(snd.content, ignoreCase = true)
            }
        } ?: return PatchApplyResult.Failure.OriginalNotFound

        val replacementTokens = TokenList(tokenize(patch.replacement))
        val updatedContent = contentTokens.replace(match, replacementTokens).text
        return PatchApplyResult.Success(updatedContent)
    } else if (patch.isRewrite) {
        return PatchApplyResult.Success(patch.replacement)
    } else {
        return PatchApplyResult.Success(content)
    }
}

/**
 * Represents the result of applying a patch to a file
 */
public sealed interface PatchApplyResult {

    /**
     * Represents a successful patch application
     */
    public data class Success(val updatedContent: String) : PatchApplyResult

    /**
     * Represents a failed patch application, including the reason for the failure
     */
    public sealed class Failure(public val reason: String) : PatchApplyResult {

        /**
         * Represents a failure to find the original text in the file content
         */
        public object OriginalNotFound : Failure(
            """
            The original text to replace was not found in the file content. 
            Consider re-reading the file to check if the original as changed since last read.
            """
        )
    }
}

@OptIn(ExperimentalContracts::class)
internal fun PatchApplyResult.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is PatchApplyResult.Success)
        returns(false) implies (this@isSuccess is PatchApplyResult.Failure)
    }
    return this is PatchApplyResult.Success
}

internal fun tokenize(
    text: String,
    separatorPattern: Regex = Regex("(\\r\\n|\\r|\\n)|[\\t ]+|[(){}\\[\\];,.:=<>/\\\\^$\"']")
): List<Token> {
    val tokens = mutableListOf<Token>()
    var start = 0
    val separators = separatorPattern.findAll(text)
    for (separator in separators) {
        if (separator.range.isEmpty()) continue
        tokens.add(Token(text.substring(start, separator.range.first), start until separator.range.first))
        tokens.add(Token(separator.value, separator.range))
        start = separator.range.endInclusive + 1
    }
    if (start < text.length) {
        tokens.add(Token(text.substring(start), start until text.length))
    }
    return tokens.filterNot { it.range.isEmpty() }
}

internal data class TokenList(val tokens: List<Token>) {
    val text = tokens.joinToString("") { it.content }

    fun find(
        other: TokenList,
        equals: (fst: Token, snd: Token) -> Boolean = { fst, snd -> fst.content == snd.content }
    ): IntRange? {
        outer@ for (i in 0..(tokens.size - other.tokens.size)) {
            for (j in other.tokens.indices) {
                if (!equals(tokens[i + j], other.tokens[j])) {
                    continue@outer
                }
            }
            return i until (i + other.tokens.size)
        }
        return null
    }

    fun replace(range: IntRange, replacement: TokenList): TokenList {
        return TokenList(
            tokens.subList(0, range.start) + replacement.tokens + tokens.subList(
                range.endInclusive + 1,
                this.tokens.size
            )
        )
    }
}

/**
 * Represents a token with its content and position in the original text
 */
internal data class Token(
    val content: String,
    val range: IntRange,
) {
    val isWhitespace: Boolean = content.isBlank()
}
