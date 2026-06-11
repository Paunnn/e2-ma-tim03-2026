package com.tim03.slagalica.util

fun evalExpression(expr: String): Int? {
    if (expr.isBlank()) return null
    return try {
        val result = Parser(expr.trim()).parse()
        if (result == result.toLong().toDouble()) result.toInt() else null
    } catch (e: Exception) {
        null
    }
}

fun removeLastToken(expr: String): String {
    val trimmed = expr.trimEnd()
    if (trimmed.isEmpty()) return ""
    return if (trimmed.last().isDigit()) {
        var start = trimmed.length - 1
        while (start > 0 && trimmed[start - 1].isDigit()) start--
        trimmed.substring(0, start).trimEnd()
    } else {
        trimmed.dropLast(1).trimEnd()
    }
}

fun recalcUsedIndices(expression: String, numbers: List<Int>): Set<Int> {
    val remaining = Regex("\\d+").findAll(expression)
        .map { it.value.toInt() }
        .toMutableList()
    val used = mutableSetOf<Int>()
    numbers.forEachIndexed { i, n ->
        if (remaining.remove(n)) used.add(i)
    }
    return used
}

private class Parser(private val input: String) {
    private var pos = 0

    fun parse(): Double {
        val result = parseExpr()
        skip()
        if (pos < input.length) throw IllegalArgumentException("Unexpected: ${input[pos]}")
        return result
    }

    private fun parseExpr(): Double {
        var result = parseTerm()
        skip()
        while (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
            val op = input[pos++]
            val term = parseTerm()
            result = if (op == '+') result + term else result - term
            skip()
        }
        return result
    }

    private fun parseTerm(): Double {
        var result = parseFactor()
        skip()
        while (pos < input.length && (input[pos] == '*' || input[pos] == '/')) {
            val op = input[pos++]
            val factor = parseFactor()
            if (op == '/') {
                if (factor == 0.0) throw ArithmeticException("Division by zero")
                result /= factor
            } else {
                result *= factor
            }
            skip()
        }
        return result
    }

    private fun parseFactor(): Double {
        skip()
        if (pos >= input.length) throw IllegalArgumentException("Unexpected end")
        return when {
            input[pos] == '(' -> {
                pos++
                val result = parseExpr()
                skip()
                if (pos < input.length && input[pos] == ')') pos++
                result
            }
            input[pos] == '-' -> {
                pos++
                -parseFactor()
            }
            input[pos].isDigit() -> {
                val start = pos
                while (pos < input.length && input[pos].isDigit()) pos++
                input.substring(start, pos).toDouble()
            }
            else -> throw IllegalArgumentException("Unexpected char: ${input[pos]}")
        }
    }

    private fun skip() {
        while (pos < input.length && input[pos] == ' ') pos++
    }
}
