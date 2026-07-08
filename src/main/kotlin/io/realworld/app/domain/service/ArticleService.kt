package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.repository.ArticleRepository

class ArticleService(private val articleRepository: ArticleRepository) {

    fun create(email: String, article: Article): Article =
        articleRepository.create(article, email)

    fun favorite(email: String, slug: String): Article =
        articleRepository.favorite(email, slug)

    fun unfavorite(email: String, slug: String): Article =
        articleRepository.unfavorite(email, slug)

    fun findPopular(email: String, limit: Int, offset: Int): List<Article> =
        articleRepository.findPopular(limit, offset, email)
}
