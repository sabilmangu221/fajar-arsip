package eu.kanade.tachiyomi.extension.id.fajar

import eu.kanade.tachiyomi.source.model.Filter

open class PartFilter(
    name: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
) {
    val selected: String?
        get() = vals[state].second.takeIf { it.isNotEmpty() }
}

class TypeFilter :
    PartFilter(
        "Jenis",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manhwa", "manhwa"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
        ),
    )

class StatusFilter :
    PartFilter(
        "Status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Tamat", "tamat"),
        ),
    )

class GenreFilter :
    PartFilter(
        "Genre",
        arrayOf(
            Pair("Semua", ""),
            Pair("Action", "action"),
            Pair("Adaptation", "adaptation"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Crime", "crime"),
            Pair("Demon", "demon"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Magic", "magic"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Regression", "regression"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Revenge", "revenge"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Super Power", "super-power"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
            Pair("Tragedy", "tragedy"),
            Pair("Webtoon", "webtoon"),
        ),
    )
