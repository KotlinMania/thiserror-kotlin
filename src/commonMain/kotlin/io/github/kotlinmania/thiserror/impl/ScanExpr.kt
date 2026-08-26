// port-lint: source impl/src/scan_expr.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Delimiter
import io.github.kotlinmania.procmacro2.Spacing
import io.github.kotlinmania.syn.BinOpParse
import io.github.kotlinmania.syn.CommaPeek
import io.github.kotlinmania.syn.Cursor
import io.github.kotlinmania.syn.IdentParse
import io.github.kotlinmania.syn.LifetimeParse
import io.github.kotlinmania.syn.LitParse
import io.github.kotlinmania.syn.ParseStream
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.PathParse
import io.github.kotlinmania.syn.PathSepPeek
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.SynType
import io.github.kotlinmania.syn.peekExpr

private sealed class InputRule {
    data class Keyword(val expected: String) : InputRule()
    data class Punct(val expected: String) : InputRule()
    data object ConsumeAny : InputRule()
    data object ConsumeBinOp : InputRule()
    data object ConsumeBrace : InputRule()
    data object ConsumeDelimiter : InputRule()
    data object ConsumeIdent : InputRule()
    data object ConsumeLifetime : InputRule()
    data object ConsumeLiteral : InputRule()
    data object ConsumeNestedBrace : InputRule()
    data object ExpectPath : InputRule()
    data object ExpectTurbofish : InputRule()
    data object ExpectType : InputRule()
    data object CanBeginExpr : InputRule()
    data object Otherwise : InputRule()
    data object Empty : InputRule()
}

private sealed class Action {
    data class SetState(val next: () -> List<Pair<InputRule, Action>>) : Action()
    data object IncDepth : Action()
    data object DecDepth : Action()
    data object Finish : Action()
}

private val INIT: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeDelimiter to Action.SetState { POSTFIX },
        InputRule.Keyword("async") to Action.SetState { ASYNC },
        InputRule.Keyword("break") to Action.SetState { BREAK_LABEL },
        InputRule.Keyword("const") to Action.SetState { CONST },
        InputRule.Keyword("continue") to Action.SetState { CONTINUE },
        InputRule.Keyword("for") to Action.SetState { FOR },
        InputRule.Keyword("if") to Action.IncDepth,
        InputRule.Keyword("let") to Action.SetState { PATTERN },
        InputRule.Keyword("loop") to Action.SetState { BLOCK },
        InputRule.Keyword("match") to Action.IncDepth,
        InputRule.Keyword("move") to Action.SetState { CLOSURE },
        InputRule.Keyword("return") to Action.SetState { RETURN },
        InputRule.Keyword("static") to Action.SetState { CLOSURE },
        InputRule.Keyword("unsafe") to Action.SetState { BLOCK },
        InputRule.Keyword("while") to Action.IncDepth,
        InputRule.Keyword("yield") to Action.SetState { RETURN },
        InputRule.Keyword("_") to Action.SetState { POSTFIX },
        InputRule.Punct("!") to Action.SetState { INIT },
        InputRule.Punct("#") to Action.SetState { listOf(InputRule.ConsumeDelimiter to Action.SetState { INIT }) },
        InputRule.Punct("&") to Action.SetState { REFERENCE },
        InputRule.Punct("*") to Action.SetState { INIT },
        InputRule.Punct("-") to Action.SetState { INIT },
        InputRule.Punct("..=") to Action.SetState { INIT },
        InputRule.Punct("..") to Action.SetState { RANGE },
        InputRule.Punct("|") to Action.SetState { CLOSURE_ARGS },
        InputRule.ConsumeLifetime to Action.SetState { listOf(InputRule.Punct(":") to Action.SetState { INIT }) },
        InputRule.ConsumeLiteral to Action.SetState { POSTFIX },
        InputRule.ExpectPath to Action.SetState { PATH },
    )
}

private val POSTFIX: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("as") to Action.SetState { listOf(InputRule.ExpectType to Action.SetState { POSTFIX }) },
        InputRule.Punct("..=") to Action.SetState { INIT },
        InputRule.Punct("..") to Action.SetState { RANGE },
        InputRule.Punct(".") to Action.SetState { DOT },
        InputRule.Punct("?") to Action.SetState { POSTFIX },
        InputRule.ConsumeBinOp to Action.SetState { INIT },
        InputRule.Punct("=") to Action.SetState { INIT },
        InputRule.ConsumeNestedBrace to Action.SetState { IF_THEN },
        InputRule.ConsumeDelimiter to Action.SetState { POSTFIX },
        InputRule.Empty to Action.Finish,
    )
}

private val ASYNC: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("move") to Action.SetState { ASYNC },
        InputRule.Punct("|") to Action.SetState { CLOSURE_ARGS },
        InputRule.ConsumeBrace to Action.SetState { POSTFIX },
    )
}

private val BLOCK: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeBrace to Action.SetState { POSTFIX },
    )
}

private val BREAK_LABEL: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeLifetime to Action.SetState { BREAK_VALUE },
        InputRule.Otherwise to Action.SetState { BREAK_VALUE },
    )
}

private val BREAK_VALUE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeNestedBrace to Action.SetState { IF_THEN },
        InputRule.CanBeginExpr to Action.SetState { INIT },
        InputRule.Otherwise to Action.SetState { POSTFIX },
    )
}

private val CLOSURE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("async") to Action.SetState { CLOSURE },
        InputRule.Keyword("move") to Action.SetState { CLOSURE },
        InputRule.Punct(",") to Action.SetState { CLOSURE },
        InputRule.Punct(">") to Action.SetState { CLOSURE },
        InputRule.Punct("|") to Action.SetState { CLOSURE_ARGS },
        InputRule.ConsumeLifetime to Action.SetState { CLOSURE },
    )
}

private val CLOSURE_ARGS: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("|") to Action.SetState { CLOSURE_RET },
        InputRule.ConsumeAny to Action.SetState { CLOSURE_ARGS },
    )
}

private val CLOSURE_RET: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("->") to Action.SetState { listOf(InputRule.ExpectType to Action.SetState { BLOCK }) },
        InputRule.Otherwise to Action.SetState { INIT },
    )
}

private val CONST: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("|") to Action.SetState { CLOSURE_ARGS },
        InputRule.ConsumeBrace to Action.SetState { POSTFIX },
    )
}

private val CONTINUE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeLifetime to Action.SetState { POSTFIX },
        InputRule.Otherwise to Action.SetState { POSTFIX },
    )
}

private val DOT: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("await") to Action.SetState { POSTFIX },
        InputRule.ConsumeIdent to Action.SetState { METHOD },
        InputRule.ConsumeLiteral to Action.SetState { POSTFIX },
    )
}

private val FOR: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("<") to Action.SetState { CLOSURE },
        InputRule.Otherwise to Action.SetState { PATTERN },
    )
}

private val IF_ELSE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("if") to Action.SetState { INIT },
        InputRule.ConsumeBrace to Action.DecDepth,
    )
}

private val IF_THEN: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("else") to Action.SetState { IF_ELSE },
        InputRule.Otherwise to Action.DecDepth,
    )
}

private val METHOD: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ExpectTurbofish to Action.SetState { POSTFIX },
    )
}

private val PATH: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("!=") to Action.SetState { INIT },
        InputRule.Punct("!") to Action.SetState { INIT },
        InputRule.ConsumeNestedBrace to Action.SetState { IF_THEN },
        InputRule.Otherwise to Action.SetState { POSTFIX },
    )
}

private val PATTERN: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.ConsumeDelimiter to Action.SetState { PATTERN },
        InputRule.Keyword("box") to Action.SetState { PATTERN },
        InputRule.Keyword("in") to Action.IncDepth,
        InputRule.Keyword("mut") to Action.SetState { PATTERN },
        InputRule.Keyword("ref") to Action.SetState { PATTERN },
        InputRule.Keyword("_") to Action.SetState { PATTERN },
        InputRule.Punct("!") to Action.SetState { PATTERN },
        InputRule.Punct("&") to Action.SetState { PATTERN },
        InputRule.Punct("..=") to Action.SetState { PATTERN },
        InputRule.Punct("..") to Action.SetState { PATTERN },
        InputRule.Punct("=") to Action.SetState { INIT },
        InputRule.Punct("@") to Action.SetState { PATTERN },
        InputRule.Punct("|") to Action.SetState { PATTERN },
        InputRule.ConsumeLiteral to Action.SetState { PATTERN },
        InputRule.ExpectPath to Action.SetState { PATTERN },
    )
}

private val RANGE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Punct("..=") to Action.SetState { INIT },
        InputRule.Punct("..") to Action.SetState { RANGE },
        InputRule.Punct(".") to Action.SetState { DOT },
        InputRule.ConsumeNestedBrace to Action.SetState { IF_THEN },
        InputRule.Empty to Action.Finish,
        InputRule.Otherwise to Action.SetState { INIT },
    )
}

private val RAW: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("const") to Action.SetState { INIT },
        InputRule.Keyword("mut") to Action.SetState { INIT },
        InputRule.Otherwise to Action.SetState { POSTFIX },
    )
}

private val REFERENCE: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.Keyword("mut") to Action.SetState { INIT },
        InputRule.Keyword("raw") to Action.SetState { RAW },
        InputRule.Otherwise to Action.SetState { INIT },
    )
}

private val RETURN: List<Pair<InputRule, Action>> by lazy {
    listOf(
        InputRule.CanBeginExpr to Action.SetState { INIT },
        InputRule.Otherwise to Action.SetState { POSTFIX },
    )
}

public fun scanExpr(input: ParseStream): SynResult<Unit> {
    var state = INIT
    var depth = 0
    while (true) {
        var matchedRule = false
        for ((rule, action) in state) {
            val matched = when (rule) {
                is InputRule.Keyword -> {
                    input.step { cursor ->
                        val pair = cursor.ident()
                        if (pair != null && pair.first.toString() == rule.expected) {
                            SynResult.success(true to pair.second)
                        } else {
                            SynResult.success(false to cursor.deref())
                        }
                    }.getOrElse { return SynResult.failure(it) }
                }
                is InputRule.Punct -> {
                    input.step { cursor ->
                        val begin = cursor.deref()
                        var current = begin
                        var matchFound = false
                        var nextCursor: Cursor = begin
                        for ((i, ch) in rule.expected.withIndex()) {
                            val pair = current.punct() ?: break
                            val punct = pair.first
                            val rest = pair.second
                            if (punct.asChar() != ch) break
                            if (i == rule.expected.length - 1) {
                                matchFound = true
                                nextCursor = rest
                                break
                            }
                            if (punct.spacing() == Spacing.Joint) {
                                current = rest
                            } else {
                                break
                            }
                        }
                        if (matchFound) {
                            SynResult.success(true to nextCursor)
                        } else {
                            SynResult.success(false to begin)
                        }
                    }.getOrElse { return SynResult.failure(it) }
                }
                is InputRule.ConsumeAny -> {
                    input.step { cursor ->
                        val tree = cursor.tokenTree()
                        if (tree != null) {
                            SynResult.success(true to tree.second)
                        } else {
                            SynResult.success(false to cursor.deref())
                        }
                    }.getOrElse { return SynResult.failure(it) }
                }
                is InputRule.ConsumeBinOp -> {
                    val fork = input.fork()
                    val binOpRes = BinOpParse.parse(fork)
                    if (binOpRes.isSuccess) {
                        input.advanceTo(fork)
                        true
                    } else {
                        false
                    }
                }
                is InputRule.ConsumeBrace, InputRule.ConsumeNestedBrace -> {
                    if (rule is InputRule.ConsumeBrace || depth > 0) {
                        input.step { cursor ->
                            val group = cursor.group(Delimiter.Brace)
                            if (group != null) {
                                SynResult.success(true to group.third)
                            } else {
                                SynResult.success(false to cursor.deref())
                            }
                        }.getOrElse { return SynResult.failure(it) }
                    } else {
                        false
                    }
                }
                is InputRule.ConsumeDelimiter -> {
                    input.step { cursor ->
                        val anyGroup = cursor.anyGroup()
                        if (anyGroup != null) {
                            SynResult.success(true to anyGroup.after)
                        } else {
                            SynResult.success(false to cursor.deref())
                        }
                    }.getOrElse { return SynResult.failure(it) }
                }
                is InputRule.ConsumeIdent -> {
                    val fork = input.fork()
                    val identRes = IdentParse.parse(fork)
                    if (identRes.isSuccess) {
                        input.advanceTo(fork)
                        true
                    } else {
                        false
                    }
                }
                is InputRule.ConsumeLifetime -> {
                    val fork = input.fork()
                    val lifeRes = LifetimeParse.parse(fork)
                    if (lifeRes.isSuccess) {
                        input.advanceTo(fork)
                        true
                    } else {
                        false
                    }
                }
                is InputRule.ConsumeLiteral -> {
                    val fork = input.fork()
                    val litRes = LitParse.parse(fork)
                    if (litRes.isSuccess) {
                        input.advanceTo(fork)
                        true
                    } else {
                        false
                    }
                }
                is InputRule.ExpectPath -> {
                    val pathRes = PathParse.parse(input)
                    if (pathRes.isFailure) {
                        return SynResult.failure(pathRes.exceptionOrNull() ?: input.error("expected path"))
                    }
                    true
                }
                is InputRule.ExpectTurbofish -> {
                    if (input.peek(PathSepPeek)) {
                        val turboRes = PathArguments.AngleBracketed.parseTurbofish(input)
                        if (turboRes.isFailure) {
                            return SynResult.failure(turboRes.exceptionOrNull() ?: input.error("expected turbofish"))
                        }
                    }
                    true
                }
                is InputRule.ExpectType -> {
                    val typeRes = SynType.withoutPlus(input)
                    if (typeRes.isFailure) {
                        return SynResult.failure(typeRes.exceptionOrNull() ?: input.error("expected type"))
                    }
                    true
                }
                is InputRule.CanBeginExpr -> peekExpr(input)
                is InputRule.Otherwise -> true
                is InputRule.Empty -> input.isEmpty() || input.peek(CommaPeek)
            }

            if (matched) {
                when (action) {
                    is Action.SetState -> {
                        state = action.next()
                    }
                    is Action.IncDepth -> {
                        depth += 1
                        state = INIT
                    }
                    is Action.DecDepth -> {
                        depth -= 1
                        state = POSTFIX
                    }
                    is Action.Finish -> {
                        return if (depth == 0) {
                            SynResult.success(Unit)
                        } else {
                            break
                        }
                    }
                }
                matchedRule = true
                break
            }
        }

        if (!matchedRule) {
            return SynResult.failure(input.error("unsupported expression"))
        }
    }
}
