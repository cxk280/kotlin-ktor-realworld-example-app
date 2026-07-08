package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.ext.slugify
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Table.PrimaryKey
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String?> = varchar("description", 500).nullable()
    val body: Column<String> = text("body")
    val authorId: Column<Long> = long("author_id")
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

internal object ArticleTags : Table() {
    val article: Column<Long> = long("article")
    val tag: Column<String> = varchar("tag", 100)
}

internal object Favorites : Table() {
    val user: Column<Long> = long("user")
    val article: Column<Long> = long("article")
    override val primaryKey = PrimaryKey(user, article)
}

class ArticleRepository {
    init {
        transaction {
            SchemaUtils.create(Articles)
            SchemaUtils.create(ArticleTags)
            SchemaUtils.create(Favorites)
        }
    }

    fun create(article: Article, authorEmail: String): Article {
        val slug = requireNotNull(article.title) { "Article title is required." }.slugify()
        val now = System.currentTimeMillis()
        transaction {
            val author = userByEmail(authorEmail) ?: throw NotFoundException("Author not found to create article.")
            val articleId = Articles.insertAndGetId { row ->
                row[Articles.slug] = slug
                row[title] = article.title
                row[description] = article.description
                row[body] = article.body
                row[authorId] = author.id!!
                row[createdAt] = now
                row[updatedAt] = now
            }.value
            article.tagList.forEach { tagName ->
                ArticleTags.insert { row ->
                    row[ArticleTags.article] = articleId
                    row[tag] = tagName
                }
            }
        }
        return findBySlug(slug, authorEmail) ?: throw NotFoundException("Article not found after create.")
    }

    fun findBySlug(slug: String, currentUserEmail: String? = null): Article? = transaction {
        Articles.select { Articles.slug eq slug }
            .map { toArticle(it, currentUserEmail) }
            .firstOrNull()
    }

    fun favorite(email: String, slug: String): Article {
        transaction {
            val user = userByEmail(email) ?: throw NotFoundException("User not found to favorite article.")
            val articleId = articleIdBySlug(slug) ?: throw NotFoundException("Article not found to favorite.")
            val alreadyFavorited = Favorites
                .select { (Favorites.article eq articleId) and (Favorites.user eq user.id!!) }
                .count() > 0
            if (!alreadyFavorited) {
                Favorites.insert { row ->
                    row[Favorites.user] = user.id!!
                    row[Favorites.article] = articleId
                }
            }
        }
        return findBySlug(slug, email) ?: throw NotFoundException("Article not found to favorite.")
    }

    fun unfavorite(email: String, slug: String): Article {
        transaction {
            val user = userByEmail(email) ?: throw NotFoundException("User not found to unfavorite article.")
            val articleId = articleIdBySlug(slug) ?: throw NotFoundException("Article not found to unfavorite.")
            Favorites.deleteWhere { sql ->
                with(sql) { (Favorites.article eq articleId) and (Favorites.user eq user.id!!) }
            }
        }
        return findBySlug(slug, email) ?: throw NotFoundException("Article not found to unfavorite.")
    }

    /**
     * Returns articles ordered by favorite count (most favorited first), paginated by [offset]/[limit].
     *
     * The favorite counts are aggregated in-app rather than via a SQL GROUP BY: Exposed 0.14.1's
     * aggregate DSL is limited, and the in-memory H2 dataset is tiny, so this stays simple and correct.
     * Ties are broken by article id (insertion order) for deterministic ordering.
     */
    fun findPopular(limit: Int, offset: Int, currentUserEmail: String? = null): List<Article> = transaction {
        val favoritesByArticle: Map<Long, Int> = Favorites.selectAll()
            .groupingBy { it[Favorites.article] }
            .eachCount()
        Articles.selectAll()
            .map { row -> row to (favoritesByArticle[row[Articles.id].value] ?: 0) }
            .sortedWith(
                compareByDescending<Pair<ResultRow, Int>> { it.second }
                    .thenBy { it.first[Articles.id].value }
            )
            .drop(offset)
            .take(limit)
            .map { toArticle(it.first, currentUserEmail) }
    }

    private fun articleIdBySlug(slug: String): Long? =
        Articles.select { Articles.slug eq slug }.firstOrNull()?.get(Articles.id)?.value

    private fun userByEmail(email: String): User? =
        Users.select { Users.email eq email }
            .map { Users.toDomain(it) }
            .firstOrNull()

    /** Assembles a full [Article] for a row. Must be called within a transaction. */
    private fun toArticle(row: ResultRow, currentUserEmail: String?): Article {
        val articleId = row[Articles.id].value
        val tags = ArticleTags.select { ArticleTags.article eq articleId }.map { it[ArticleTags.tag] }
        val favoritesCount = Favorites.select { Favorites.article eq articleId }.count().toLong()
        val favorited = currentUserEmail?.let { email ->
            userByEmail(email)?.let { user ->
                Favorites.select { (Favorites.article eq articleId) and (Favorites.user eq user.id!!) }.count() > 0
            }
        } ?: false
        val author = Users.select { Users.id eq EntityID(row[Articles.authorId], Users) }
            .map { Users.toDomain(it) }
            .firstOrNull()
            ?.copy(password = null, token = null)
        return Article(
            slug = row[Articles.slug],
            title = row[Articles.title],
            description = row[Articles.description],
            body = row[Articles.body],
            tagList = tags,
            createdAt = Date(row[Articles.createdAt]),
            updatedAt = Date(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author
        )
    }
}
