package tech.nothing.agentos.glyph

/**
 * A proportional 5x7 pixel font for the Glyph Matrix. Each glyph is seven rows of five
 * columns; empty columns at either side are trimmed, so "1", "." and ":" sit tight.
 *
 * prototype/agent.js carries the same table: keep the two in sync.
 */
object MatrixFont {
    const val HEIGHT = 7
    private const val GAP = 1
    private const val SPACE = 3

    private val SOURCE: Map<Char, String> = mapOf(
        'A' to "01110 10001 10001 11111 10001 10001 10001",
        'B' to "11110 10001 10001 11110 10001 10001 11110",
        'C' to "01110 10001 10000 10000 10000 10001 01110",
        'D' to "11110 10001 10001 10001 10001 10001 11110",
        'E' to "11111 10000 10000 11110 10000 10000 11111",
        'F' to "11111 10000 10000 11110 10000 10000 10000",
        'G' to "01110 10001 10000 10111 10001 10001 01111",
        'H' to "10001 10001 10001 11111 10001 10001 10001",
        'I' to "01110 00100 00100 00100 00100 00100 01110",
        'J' to "00111 00010 00010 00010 00010 10010 01100",
        'K' to "10001 10010 10100 11000 10100 10010 10001",
        'L' to "10000 10000 10000 10000 10000 10000 11111",
        'M' to "10001 11011 10101 10101 10001 10001 10001",
        'N' to "10001 10001 11001 10101 10011 10001 10001",
        'O' to "01110 10001 10001 10001 10001 10001 01110",
        'P' to "11110 10001 10001 11110 10000 10000 10000",
        'Q' to "01110 10001 10001 10001 10101 10010 01101",
        'R' to "11110 10001 10001 11110 10100 10010 10001",
        'S' to "01111 10000 10000 01110 00001 00001 11110",
        'T' to "11111 00100 00100 00100 00100 00100 00100",
        'U' to "10001 10001 10001 10001 10001 10001 01110",
        'V' to "10001 10001 10001 10001 10001 01010 00100",
        'W' to "10001 10001 10001 10101 10101 10101 01010",
        'X' to "10001 10001 01010 00100 01010 10001 10001",
        'Y' to "10001 10001 10001 01010 00100 00100 00100",
        'Z' to "11111 00001 00010 00100 01000 10000 11111",
        '0' to "01110 10001 10011 10101 11001 10001 01110",
        '1' to "00100 01100 00100 00100 00100 00100 01110",
        '2' to "01110 10001 00001 00010 00100 01000 11111",
        '3' to "11111 00010 00100 00010 00001 10001 01110",
        '4' to "00010 00110 01010 10010 11111 00010 00010",
        '5' to "11111 10000 11110 00001 00001 10001 01110",
        '6' to "00110 01000 10000 11110 10001 10001 01110",
        '7' to "11111 00001 00010 00100 01000 01000 01000",
        '8' to "01110 10001 10001 01110 10001 10001 01110",
        '9' to "01110 10001 10001 01111 00001 00010 01100",
        '.' to "00000 00000 00000 00000 00000 00100 00100",
        ',' to "00000 00000 00000 00000 00100 00100 01000",
        '\'' to "00100 00100 01000 00000 00000 00000 00000",
        ':' to "00000 00100 00100 00000 00100 00100 00000",
        '?' to "01110 10001 00001 00010 00100 00000 00100",
        '!' to "00100 00100 00100 00100 00100 00000 00100",
        '-' to "00000 00000 00000 11111 00000 00000 00000",
        '+' to "00000 00100 00100 11111 00100 00100 00000",
        '/' to "00000 00001 00010 00100 01000 10000 00000",
        '%' to "11000 11001 00010 00100 01000 10011 00011",
    )

    /** Glyph columns, left to right; bit r of a column = row r lit (row 0 at the top). */
    private val glyphs: Map<Char, IntArray> = SOURCE.mapValues { (_, rows) ->
        val r = rows.split(' ')
        val cols = (0 until 5).map { c -> (0 until HEIGHT).fold(0) { acc, row -> if (r[row][c] == '1') acc or (1 shl row) else acc } }
        val first = cols.indexOfFirst { it != 0 }
        val last = cols.indexOfLast { it != 0 }
        cols.subList(first, last + 1).toIntArray()
    }

    private fun normalise(c: Char): Char = when (c) {
        '‘', '’' -> '\''
        '“', '”', '"' -> '\''
        '–', '—' -> '-'
        else -> c.uppercaseChar()
    }

    /** Lays out [text] as a list of columns (bitmasks). Unknown characters become spaces. */
    fun columns(text: String): IntArray {
        val out = ArrayList<Int>()
        for (ch in text) {
            val g = glyphs[normalise(ch)]
            if (g == null) {
                repeat(SPACE) { out += 0 }
                continue
            }
            if (out.isNotEmpty() && out.last() != 0) repeat(GAP) { out += 0 }
            g.forEach { out += it }
        }
        while (out.isNotEmpty() && out.last() == 0) out.removeAt(out.lastIndex)
        return out.toIntArray()
    }
}
