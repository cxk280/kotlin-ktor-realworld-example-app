package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for `GET /api/articles/feed/popular` (articles ranked by favorites count).
 *
 * Boots the full app via [AppRule] and drives it over HTTP with [HttpUtil]/Unirest, matching the
 * existing controller-test style. The in-memory H2 database is shared across test methods within the
 * JVM, so tests use unique identifiers and relative (not absolute-position) assertions to stay
 * independent of any data left behind by sibling tests.
 */
class ArticlePopularFeedControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private val seq = AtomicInteger(0)

    @Test
    fun `popular feed ranks articles by favorites count and is paginated`() {
        val author = newAuthedUser("author")
        val mostFavoritedSlug = author.createArticle("Popular Most")
        val leastFavoritedSlug = author.createArticle("Popular Least")

        val fanA = newAuthedUser("fanA")
        val fanB = newAuthedUser("fanB")
        // Most-favorited article gets 2 favorites, least-favorited gets 1.
        assertEquals(HttpStatus.SC_OK, fanA.favorite(mostFavoritedSlug))
        assertEquals(HttpStatus.SC_OK, fanB.favorite(mostFavoritedSlug))
        assertEquals(HttpStatus.SC_OK, fanA.favorite(leastFavoritedSlug))

        val response = author.get<ArticlesDTO>("/api/articles/feed/popular?limit=100&offset=0")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(response.body.articles.size, response.body.articlesCount)

        val articles = response.body.articles
        val mostIndex = articles.indexOfFirst { it.slug == mostFavoritedSlug }
        val leastIndex = articles.indexOfFirst { it.slug == leastFavoritedSlug }
        assertTrue("most-favorited article should be present", mostIndex >= 0)
        assertTrue("least-favorited article should be present", leastIndex >= 0)
        assertTrue("more-favorited article must rank ahead of less-favorited one", mostIndex < leastIndex)
        assertEquals(2L, articles[mostIndex].favoritesCount)
        assertEquals(1L, articles[leastIndex].favoritesCount)
        assertTrue("returned article should carry its tags", articles[mostIndex].tagList.isNotEmpty())
    }

    @Test
    fun `popular feed limit and offset return different pages`() {
        val author = newAuthedUser("pager")
        // Ensure at least two articles exist globally so paging can advance.
        author.createArticle("Page One")
        author.createArticle("Page Two")

        val firstPage = author.get<ArticlesDTO>("/api/articles/feed/popular?limit=1&offset=0")
        val secondPage = author.get<ArticlesDTO>("/api/articles/feed/popular?limit=1&offset=1")

        assertEquals(HttpStatus.SC_OK, firstPage.status)
        assertEquals(HttpStatus.SC_OK, secondPage.status)
        assertEquals(1, firstPage.body.articles.size)
        assertEquals(1, secondPage.body.articles.size)
        assertNotEquals(
            "offset should advance to a different article",
            firstPage.body.articles.first().slug,
            secondPage.body.articles.first().slug
        )
    }

    @Test
    fun `popular feed requires authentication`() {
        val anonymous = HttpUtil(appRule.port) // no Authorization header set
        val response = anonymous.getString("/api/articles/feed/popular")

        assertEquals(HttpStatus.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `popular feed rejects invalid pagination params`() {
        val user = newAuthedUser("bad_input")
        val response = user.getString("/api/articles/feed/popular?limit=not-a-number")

        assertEquals(422, response.status)
    }

    private fun newAuthedUser(role: String): HttpUtil {
        val http = HttpUtil(appRule.port)
        val suffix = "${role}_${System.currentTimeMillis()}_${seq.incrementAndGet()}"
        http.createUser("$suffix@valid_email.com", "user_$suffix")
        return http
    }

    private fun HttpUtil.createArticle(titlePrefix: String): String {
        val title = "$titlePrefix ${System.currentTimeMillis()}_${seq.incrementAndGet()}"
        val article = Article(
            title = title,
            description = "Ever wonder how?",
            body = "Very carefully.",
            tagList = listOf("popular", "dragons")
        )
        val response = post<ArticleDTO>("/api/articles", ArticleDTO(article))
        assertEquals(HttpStatus.SC_OK, response.status)
        return response.body.article?.slug ?: error("create article returned no slug")
    }

    private fun HttpUtil.favorite(slug: String): Int =
        post<ArticleDTO>("/api/articles/$slug/favorite").status
}
