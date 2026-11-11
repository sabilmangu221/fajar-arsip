package eu.kanade.tachiyomi.extension.id.fajar

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class Fajar :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private val docHeaders: Headers
        get() = headersBuilder()
            .removeAll("User-Agent")
            .add("User-Agent", USER_AGENT)
            .build()

    private val apiHeaders: Headers
        get() = headersBuilder()
            .removeAll("User-Agent")
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json")
            .build()

    // ====================== Domain ======================

    private fun normalizeBase(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun siteBase(): String =
        normalizeBase(preferences.getString(PREF_DOMAIN, "")) ?: baseUrl

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN
            title = "Domain (override)"
            summary = "Kosongkan untuk otomatis. Isi hanya kalau alamat situs berubah."
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    private suspend fun getDocument(url: HttpUrl): Document =
        client.get(url, docHeaders, Fetch.DOCUMENT).asJsoup()

    // ====================== Popular ======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val document = getDocument("${siteBase()}/peringkat".toHttpUrl())
        return MangasPage(parseCards(document), false)
    }

    // ====================== Latest ======================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page > 1) "${siteBase()}/jelajah/page/$page" else "${siteBase()}/jelajah"
        val document = getDocument(path.toHttpUrl())
        return MangasPage(parseCards(document), hasNextPage(document, page))
    }

    // ====================== Search ======================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.filterIsInstance<TypeFilter>().firstOrNull()?.selected
        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.selected
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selected

        if (query.isNotEmpty()) {
            if (page > 1) return MangasPage(emptyList(), false)
            val url = "${siteBase()}/cari".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .apply { if (type != null) addQueryParameter("tipe", type) }
                .build()
            return MangasPage(parseCards(getDocument(url)), false)
        }

        val base = when {
            genre != null -> "${siteBase()}/jelajah/genre/$genre"
            type != null -> "${siteBase()}/jelajah/$type"
            else -> "${siteBase()}/jelajah"
        }
        val path = if (page > 1) "$base/page/$page" else base
        val url = path.toHttpUrl().newBuilder()
            .apply { if (status != null) addQueryParameter("status", status) }
            .build()
        val document = getDocument(url)
        return MangasPage(parseCards(document), hasNextPage(document, page))
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private fun hasNextPage(document: Document, page: Int): Boolean =
        document.select("a[href]").any { it.attr("href").contains("/page/${page + 1}") }

    private fun parseCards(document: Document): List<SManga> =
        document.select("a.bm-card[href^=/komik/], a.bm-row[href^=/komik/]").mapNotNull { a ->
            val href = a.attr("href")
            val img = a.selectFirst("img")
            val title = a.selectFirst(".bm-card__title, .bm-row__title")?.text()?.trim()
                ?: img?.attr("alt")?.removePrefix("Sampul ")?.trim()
                ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            SManga.create().apply {
                url = href
                this.title = title
                thumbnail_url = img?.let {
                    it.absUrl("src").ifBlank { it.absUrl("data-src") }.ifBlank { null }
                }
            }
        }.distinctBy { it.url }

    // ====================== Details & Chapters ======================

    override fun getMangaUrl(manga: SManga): String = "${siteBase()}${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "${siteBase()}${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != "mwland.my" && !url.host.endsWith(".mwland.my")) return null
        if (url.pathSegments.firstOrNull() != "komik") return null
        val manga = SManga.create().apply {
            this.url = url.encodedPath
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = if (fetchDetails) {
            async { fetchMangaDetails(manga) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapterList(manga) }
        } else {
            null
        }
        SMangaUpdate(detailsDeferred?.await() ?: manga, chaptersDeferred?.await() ?: chapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = getDocument("${siteBase()}${manga.url}".toHttpUrl())
        return manga.apply {
            document.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { title = it }
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?.takeIf { it.isNotBlank() }?.let { thumbnail_url = it }

            val info = document.select("dl dt").associate { dt ->
                dt.text().trim() to (dt.nextElementSibling()?.text()?.trim().orEmpty())
            }
            author = info["Cerita oleh"].orEmpty()
            status = when {
                info["Status cerita"]?.contains("Tamat", true) == true -> SManga.COMPLETED
                info["Status cerita"]?.contains("Berlanjut", true) == true -> SManga.ONGOING
                info["Status cerita"]?.contains("Hiatus", true) == true -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            val genres = info["Genre"]?.split(",")
                ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val type = info["Jenis"]?.takeIf { it.isNotBlank() }
            genre = (genres + listOfNotNull(type)).joinToString()
            description = extractDescription(document)
        }
    }

    private fun extractDescription(document: Document): String {
        val meta = document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        val fromMeta = meta.substringAfter("Sinopsis:", "").trim()
        if (fromMeta.isNotEmpty()) return fromMeta
        val faq = document.select("h3, h4")
            .firstOrNull { it.text().contains("inti cerita", true) }
            ?.nextElementSibling()?.text()?.trim().orEmpty()
        return faq.removePrefix("Sinopsis:").trim().ifEmpty { meta }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val idSlug = manga.url.removePrefix("/komik/")
        val slug = idSlug.replace(Regex("^\\d+-"), "")
        val url = "${siteBase()}/api/komik/$slug/chapters".toHttpUrl()
        val dto = try {
            client.get(url, apiHeaders, Fetch.XHR).parseAs<FajarChaptersDto>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Daftar chapter gagal dimuat: ${e.message}")
        }
        return dto.chapters.mapNotNull { entry ->
            val number = entry.getOrNull(1)?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            SChapter.create().apply {
                name = "Chapter $number"
                chapter_number = number.toFloatOrNull() ?: -1f
                date_upload = entry.getOrNull(2)?.jsonPrimitive?.contentOrNull
                    ?.let { Instant.tryParse(it) } ?: 0L
                this.url = "/baca/$idSlug/chapter-$number"
            }
        }
    }

    // ====================== Page List ======================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = getDocument("${siteBase()}${chapter.url}".toHttpUrl())
        val pages = document.select(".bm-reader__pages img").mapIndexedNotNull { index, img ->
            val src = img.absUrl("src").ifBlank { img.absUrl("data-src") }
            if (src.isBlank()) null else Page(index, imageUrl = src)
        }
        if (pages.isEmpty() && document.body().text().contains("Gabung VIP untuk membaca")) {
            throw Exception("Chapter ini terkunci di situs sumbernya")
        }
        return pages
    }

    override fun getImageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .removeAll("User-Agent")
            .add("User-Agent", USER_AGENT)
            .add("Referer", "${siteBase()}/")
            .build()
        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(imageHeaders)
            .get()
            .build()
    }

    companion object {
        private const val PREF_DOMAIN = "domain"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; V2453) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}
