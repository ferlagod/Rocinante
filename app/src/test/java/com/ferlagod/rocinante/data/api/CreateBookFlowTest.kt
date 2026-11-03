/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.api

import com.ferlagod.rocinante.data.api.BookWyrmScraper.AuthorOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateBookFlowTest {

    private val base = "https://bookwyrm.es"

    @Test
    fun `cada autor va en su propio add_author y los vacios no se envian`() {
        val fields = BookWyrmScraper.buildCreateBookFields(
            csrfToken = "t", title = " Buenos presagios ",
            authorNames = listOf("Terry Pratchett", " ", "Neil Gaiman", "Terry Pratchett"),
            isbn = "84-450-7187-X", pages = "", publishers = "Minotauro", series = ""
        )
        assertEquals(listOf("Terry Pratchett", "Neil Gaiman"), fields.filter { it.first == "add_author" }.map { it.second })
        assertTrue("title" to "Buenos presagios" in fields)
        assertTrue("isbn_10" to "844507187X" in fields)
        assertTrue("publishers" to "Minotauro" in fields)
        assertFalse(fields.any { it.first == "pages" || it.first == "series" || it.first == "published_date" })
    }

    // Tal cual la pantalla de confirmación de BookWyrm (book/edit/edit_book.html) con dos autores.
    private val confirmHtml = """
        <form name="create-book" action="/create-book/confirm" method="post" enctype="multipart/form-data">
          <input type="hidden" name="csrfmiddlewaretoken" value="abc">
          <input type="hidden" name="author-match-count" value="2">
          <fieldset><legend>Is "Terry Pratchett" one of these authors?</legend>
            <label class="label mb-0"><input type="radio" name="author_match-0" value="12" required> Terry Pratchett</label>
            <p class="help ml-5 mb-2"><a href="/author/12">Author of Mort</a></p>
            <label class="label mt-4"><input type="radio" name="author_match-0" value="Terry Pratchett" required> Create new author</label>
          </fieldset>
          <fieldset><legend>Is "Neil Gaiman" one of these authors?</legend>
            <label class="label mt-4"><input type="radio" name="author_match-1" value="Neil Gaiman" required> Create new author</label>
          </fieldset>
          <label><input type="radio" name="parent_work" value="77"> Good Omens</label>
          <label><input type="radio" name="parent_work" value="0" required> This is a new work</label>
          <input type="text" name="title" value="Buenos presagios" readonly>
          <input type="text" name="add_author" value="Terry Pratchett" readonly>
          <input type="text" name="add_author" value="Neil Gaiman" readonly>
          <input type="text" name="isbn_13" value="">
          <button type="submit">Confirm</button>
        </form>
    """.trimIndent()

    @Test
    fun `la confirmacion agrupa autores y obra y no se cuela ningun redondel en los campos`() {
        val step = BookWyrmScraper.parseCreateConfirmation(confirmHtml, "$base/create-book", base)
        assertTrue(step is BookWyrmScraper.ConfirmationStep.Ask)
        step as BookWyrmScraper.ConfirmationStep.Ask
        assertEquals("$base/create-book/confirm", step.form.actionUrl)
        val groups = step.options.groupBy { it.field }
        assertEquals(setOf("author_match-0", "author_match-1", "parent_work"), groups.keys)
        // Autores: primero el existente. Obra: primero la nueva, como hasta ahora.
        assertEquals(AuthorOption.Kind.EXISTING, groups.getValue("author_match-0").first().kind)
        assertEquals("0", groups.getValue("parent_work").first().value)
        assertEquals(AuthorOption.Kind.EXISTING, groups.getValue("parent_work")[1].kind)
        assertFalse(step.form.fields.any { it.first.startsWith("author_match-") || it.first == "parent_work" })
        assertTrue("author-match-count" to "2" in step.form.fields)
    }

    @Test
    fun `al elegir un autor existente se quita solo su add_author`() {
        val step = BookWyrmScraper.parseCreateConfirmation(confirmHtml, "$base/create-book", base) as BookWyrmScraper.ConfirmationStep.Ask
        val groups = step.options.groupBy { it.field }
        val selections = listOf(
            groups.getValue("author_match-0").first { it.value == "12" },
            groups.getValue("author_match-1").first(),
            groups.getValue("parent_work").first { it.value == "77" }
        )
        val fields = BookWyrmScraper.buildConfirmFields(step.form, selections)
        assertEquals(listOf("Neil Gaiman"), fields.filter { it.first == "add_author" }.map { it.second })
        assertTrue("author_match-0" to "12" in fields)
        assertTrue("author_match-1" to "Neil Gaiman" in fields)
        assertEquals(listOf("77"), fields.filter { it.first == "parent_work" }.map { it.second })
        assertFalse(fields.any { it.first == "isbn_13" })
    }

    @Test
    fun `sin nada que elegir se confirma con obra nueva`() {
        val html = """
            <form action="/create-book/confirm" method="post">
              <input type="hidden" name="csrfmiddlewaretoken" value="abc">
              <label><input type="radio" name="parent_work" value="0" required> This is a new work</label>
              <input type="text" name="title" value="Libro">
            </form>
        """.trimIndent()
        val step = BookWyrmScraper.parseCreateConfirmation(html, "$base/create-book", base)
        assertTrue(step is BookWyrmScraper.ConfirmationStep.Auto)
        step as BookWyrmScraper.ConfirmationStep.Auto
        assertEquals(listOf("0"), step.form.fields.filter { it.first == "parent_work" }.map { it.second })
    }

    @Test
    fun `la serie existente se pregunta y la nueva va primero`() {
        val html = """
            <form action="/create-book/confirm" method="post">
              <input type="hidden" name="confirm_series_mode" value="True">
              <label><input type="radio" name="series_match" value="5"> Mundodisco</label>
              <label><input type="radio" name="series_match" value="0" required> This is a new series</label>
            </form>
        """.trimIndent()
        val step = BookWyrmScraper.parseCreateConfirmation(html, "$base/create-book/confirm", base)
        assertTrue(step is BookWyrmScraper.ConfirmationStep.Ask)
        step as BookWyrmScraper.ConfirmationStep.Ask
        assertEquals(listOf("0", "5"), step.options.map { it.value })
        assertTrue("confirm_series_mode" to "True" in step.form.fields)
    }

    @Test
    fun `una pagina de login es un error y no una confirmacion`() {
        val html = """<form action="/login"><input name="username"><input name="password" type="password"></form>"""
        assertTrue(BookWyrmScraper.parseCreateConfirmation(html, "$base/create-book", base) is BookWyrmScraper.ConfirmationStep.Failed)
    }
}
