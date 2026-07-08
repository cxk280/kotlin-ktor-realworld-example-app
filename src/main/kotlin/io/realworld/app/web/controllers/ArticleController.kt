package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {

    fun findBy(ctx: ApplicationCall): ArticlesDTO {
        val tag = ctx.parameters["tag"]
        val author = ctx.parameters["author"]
        val favorited = ctx.parameters["favorited"]
        val limit = ctx.parameters["limit"] ?: "20"
        val offset = ctx.parameters["offset"] ?: "0"
//        articleService.findBy(tag, author, favorited, limit.toInt(), offset.toInt()).also { articles ->
//            ctx.json(ArticlesDTO(articles, articles.size))
//        }
        return ArticlesDTO(listOf(), 1)
    }

    fun feed(ctx: ApplicationCall): ArticlesDTO {
        val limit = ctx.parameters["limit"] ?: "20"
        val offset = ctx.parameters["offset"] ?: "0"
//        articleService.findFeed(ctx.attribute("email"), limit.toInt(), offset.toInt()).also { articles ->
//            ctx.json(ArticlesDTO(articles, articles.size))
//        }
        return ArticlesDTO(listOf(), 1)
    }

    /**
     * GET /api/articles/feed/popular — articles sorted by favorites count (most favorited first),
     * with limit/offset pagination. Requires authentication.
     */
    suspend fun feedPopular(ctx: ApplicationCall) {
        val email = currentUserEmail(ctx)
        val limit = parsePositiveInt(ctx.parameters["limit"], default = 20, name = "limit")
        val offset = parsePositiveInt(ctx.parameters["offset"], default = 0, name = "offset")
        articleService.findPopular(email, limit, offset).also { articles ->
            ctx.respond(ArticlesDTO(articles, articles.size))
        }
    }

    fun get(ctx: ApplicationCall): ArticleDTO {
        ctx.parameters["slug"]
        //                articleService.findBySlug(slug).apply {
//                    ctx.json(ArticleDTO(this))
//                }
        return ArticleDTO(null)
    }

    suspend fun create(ctx: ApplicationCall) {
        val email = currentUserEmail(ctx)
        val article = ctx.receive<ArticleDTO>().article
        require(article != null && !article.title.isNullOrBlank() && article.body.isNotBlank()) {
            "Article title and body are required."
        }
        articleService.create(email, article).apply {
            ctx.respond(ArticleDTO(this))
        }
    }

    suspend fun update(ctx: ApplicationCall): ArticleDTO {
        val slug = ctx.parameters["slug"]
        ctx.receive<ArticleDTO>()
        //            articleService.update(slug, article).apply {
//                ctx.json(ArticleDTO(this))
//            }
        return ArticleDTO(null)
    }

    fun delete(ctx: ApplicationCall) {
        ctx.parameters["slug"]
        //            articleService.delete(slug)
    }

    suspend fun favorite(ctx: ApplicationCall) {
        val email = currentUserEmail(ctx)
        val slug = requireNotNull(ctx.parameters["slug"]) { "Article slug is required." }
        articleService.favorite(email, slug).apply {
            ctx.respond(ArticleDTO(this))
        }
    }

    suspend fun unfavorite(ctx: ApplicationCall) {
        val email = currentUserEmail(ctx)
        val slug = requireNotNull(ctx.parameters["slug"]) { "Article slug is required." }
        articleService.unfavorite(email, slug).apply {
            ctx.respond(ArticleDTO(this))
        }
    }

    private fun currentUserEmail(ctx: ApplicationCall): String {
        val email = ctx.authentication.principal<User>()?.email
        require(!email.isNullOrBlank()) { "User not logged in." }
        return email
    }

    private fun parsePositiveInt(value: String?, default: Int, name: String): Int {
        if (value == null) return default
        val parsed = value.toIntOrNull()
        require(parsed != null && parsed >= 0) { "Query parameter '$name' must be a non-negative integer." }
        return parsed
    }
}
