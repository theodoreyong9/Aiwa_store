package com.aiwa.bridge

/** One thing a repository holds (a film, a document, a page, an Aiwa contract), and where to open it. */
data class AllItem(val repo: String, val name: String, val url: String)

/** The « Tous » view of one kind: the items of every repository, how many repositories were looked at and how many could not be read. */
data class AllResult(val ok: Boolean, val items: List<AllItem>, val repos: Int, val unreadable: Int, val error: String)
