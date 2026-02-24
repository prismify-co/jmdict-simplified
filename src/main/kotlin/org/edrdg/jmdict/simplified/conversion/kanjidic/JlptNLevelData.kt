package org.edrdg.jmdict.simplified.conversion.kanjidic

/**
 * Loads JLPT N-level (post-2010, N1–N5) kanji data from a bundled resource file.
 *
 * Source: AnchorI/jlpt-kanji-dictionary (MIT license), derived from tanos.co.uk (CC-BY).
 * These are community-curated approximations — official JLPT kanji lists do not exist
 * for the N-level system introduced in 2010.
 *
 * The resource file format is TSV: each non-comment line is `LEVEL\tKANJI_STRING`,
 * where LEVEL is 1–5 (1 = most advanced, 5 = most elementary) and each character
 * in KANJI_STRING belongs to that level.
 */
object JlptNLevelData {
    /**
     * Map from kanji character (as a String) to its JLPT N-level (1–5).
     * Kanji not in this map have no known JLPT N-level assignment.
     */
    val kanjiToLevel: Map<String, Int> by lazy { loadFromResource() }

    private fun loadFromResource(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val stream = JlptNLevelData::class.java.getResourceAsStream("/jlpt-n-levels.tsv")
            ?: throw IllegalStateException("Resource /jlpt-n-levels.tsv not found on classpath")

        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.split("\t", limit = 2)
                if (parts.size != 2) continue
                val level = parts[0].trim().toIntOrNull() ?: continue
                val kanjiString = parts[1].trim()
                for (char in kanjiString) {
                    val kanji = char.toString()
                    if (result.containsKey(kanji)) {
                        System.err.println("WARNING: Duplicate kanji '$kanji' found in JLPT N-level data (level $level), already assigned to level ${result[kanji]}")
                    }
                    result[kanji] = level
                }
            }
        }

        if (result.isEmpty()) {
            throw IllegalStateException("No JLPT N-level data loaded from /jlpt-n-levels.tsv")
        }

        return result
    }
}
