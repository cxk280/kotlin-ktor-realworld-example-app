package io.realworld.app.ext

const val MAIL_REGEX = ("^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@"
    + "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
    + "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\."
    + "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
    + "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|"
    + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$")

fun String.isEmailValid(): Boolean = !this.isNullOrBlank() && Regex(MAIL_REGEX).matches(this)

/**
 * Turns a title into a URL-friendly slug, e.g. "Slug Test" -> "slug-test".
 * Lowercases, drops non-alphanumerics, and collapses whitespace/dashes into single hyphens.
 */
fun String.slugify(): String = this.trim()
    .toLowerCase()
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .replace(Regex("[\\s-]+"), "-")
    .trim('-')
