import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate

/** Dependency-free executable regression suite, invoked through --self-test. */
object DocumentTests {
    private var checks = 0
    private val today = LocalDate.parse("2026-12-31")

    private fun assertThat(condition: Boolean, message: String) {
        checks++
        check(condition) { message }
    }

    private fun <T> assertEquals(expected: T, actual: T) {
        checks++
        check(expected == actual) { "Esperado <$expected>, obtido <$actual>" }
    }

    private fun assertFails(block: () -> Unit) {
        checks++
        check(runCatching(block).isFailure) { "Era esperada uma falha de validação" }
    }

    private fun fixture() = listOf(
        Document("CNH; renovar", today.minusDays(1), "Pessoal", "Renovar, urgente", 7),
        Document("Seguro", today, "Veículos", "", 1),
        Document("Passaporte", today.plusDays(10), "Pessoal"),
        Document("Garantia notebook", today.plusDays(90), "Compras")
    )

    private fun testValidation() {
        val doc = Document("CNH", today)
        assertEquals(doc, doc.validated())
        assertFails { doc.copy(title = "").validated() }
        assertFails { doc.copy(title = " ").validated() }
        assertFails { doc.copy(title = "a".repeat(81)).validated() }
        assertFails { doc.copy(title = "x\nBEGIN:VEVENT").validated() }
        assertFails { doc.copy(title = "x\tvalue").validated() }
        assertFails { doc.copy(category = "").validated() }
        assertFails { doc.copy(category = "a".repeat(41)).validated() }
        assertFails { doc.copy(notes = "a".repeat(201)).validated() }
        assertFails { doc.copy(notes = "bad\rvalue").validated() }
        assertFails { doc.copy(reminderDays = -1).validated() }
        assertFails { doc.copy(reminderDays = 366).validated() }
        assertFails { doc.copy(date = LocalDate.of(1899, 12, 31)).validated() }
        assertFails { doc.copy(date = LocalDate.of(9999, 1, 1)).validated() }
        assertThat(Document("São João", today).validated().title.contains("ã"), "Unicode deve ser aceito")
    }

    private fun testTsv() {
        val old = "titulo\tvalidade\nCNH\t2026-12-31\n"
        assertEquals(listOf(Document("CNH", today)), parseDocuments(old))
        assertEquals(listOf(Document("CNH", today)), parseDocuments("\uFEFF" + old))
        assertEquals(listOf(Document("CNH", today)), parseDocuments(old.replace("\n", "\r\n")))
        val all = fixture()
        assertEquals(all.sortedBy { it.date }, parseDocuments(serializeDocuments(all)))
        assertThat(serializeDocuments(emptyList()).startsWith("titulo\tvalidade"), "Cabeçalho obrigatório")
        assertFails { parseDocuments("") }
        assertFails { parseDocuments("foo\tbar\n") }
        assertFails { parseDocuments("titulo\tvalidade\nCNH\n") }
        assertFails { parseDocuments("titulo\tvalidade\nCNH\t2026-02-30\n") }
        assertFails { parseDocuments("titulo\tvalidade\nCNH\t2026-12-31\textra\n") }
        assertFails { parseDocuments("titulo\tvalidade\nCNH\t2026-12-31\ncnH\t2026-12-31\n") }
        assertFails { parseDocuments("titulo\tvalidade\tcategoria\tnotas\tlembrete_dias\nCNH\t2026-12-31\tGeral\t\tfoo\n") }
        assertFails { serializeDocuments(listOf(Document("A", today), Document("a", today))) }
    }

    private fun testMutations() {
        val original = fixture()
        val added = addDocument(original, Document("Certificado", today.plusDays(50)))
        assertEquals(5, added.size)
        assertEquals(4, original.size)
        assertFails { addDocument(original, original.first()) }
        assertFails { addDocument(original, original.first().copy(title = "cnh; RENOVAR")) }
        val removed = removeDocument(added, "CERTIFICADO", today.plusDays(50))
        assertEquals(original, removed)
        assertFails { removeDocument(original, "Inexistente", today) }
        val replaced = updateDocument(original, "seguro", today, Document("Seguro", today.plusDays(30)))
        assertEquals(today.plusDays(30), replaced[1].date)
        assertFails { updateDocument(original, "Inexistente", today, Document("A", today)) }
        assertFails { updateDocument(original, "Seguro", today, original.first()) }
        assertEquals("a|2026-12-31", documentKey(Document(" A ", today)))
    }

    private fun testStatusAndReport() {
        val rows = listDocuments(fixture(), today)
        assertEquals(4, rows.size)
        assertEquals(listOf(-1L, 0L, 10L, 90L), rows.map { it.daysRemaining })
        assertEquals(listOf(ExpiryStatus.VENCIDO, ExpiryStatus.VENCE_HOJE, ExpiryStatus.PROXIMO, ExpiryStatus.EM_DIA), rows.map { it.status })
        assertEquals(3, listDocuments(fixture(), today, onlyAttention = true).size)
        assertEquals(2, listDocuments(fixture(), today, category = "pessoal").size)
        assertEquals(0, listDocuments(fixture(), today, category = "Inexistente").size)
        assertEquals(ExpiryStatus.EM_DIA, classifyDocument(Document("A", today.plusDays(1)), today, 0).status)
        assertEquals(ExpiryStatus.PROXIMO, classifyDocument(Document("A", today.plusDays(1)), today, 1).status)
        assertFails { classifyDocument(Document("A", today), today, -1) }
        assertFails { listDocuments(fixture(), today, 3651) }
        val report = buildReport(fixture(), today)
        assertEquals(4, report.total)
        assertEquals(1, report.expired)
        assertEquals(1, report.dueToday)
        assertEquals(1, report.upcoming)
        assertEquals(1, report.valid)
        assertEquals(2, report.byCategory["Pessoal"])
        assertEquals(0, buildReport(emptyList(), today).total)
        assertEquals(0, buildReport(emptyList(), today).expired)
    }

    private fun testCalendar() {
        val ics = calendar(fixture(), today)
        assertThat(ics.startsWith("BEGIN:VCALENDAR\r\n"), "Cabeçalho ICS")
        assertThat(ics.endsWith("END:VCALENDAR\r\n"), "Rodapé ICS")
        assertEquals(4, "BEGIN:VEVENT".toRegex().findAll(ics).count())
        assertThat(ics.contains("DTEND;VALUE=DATE:20270101"), "Evento de dia inteiro")
        assertThat(ics.contains("SUMMARY:CNH\\; renovar"), "Escapar ponto e vírgula")
        assertThat(ics.contains("DESCRIPTION:Renovar\\, urgente"), "Escapar vírgula")
        assertThat(ics.contains("TRIGGER:-P7D"), "Lembrete em dias")
        assertThat(ics.contains("CATEGORIES:Pessoal"), "Categoria no calendário")
        assertEquals(ics, calendar(fixture(), today))
        assertThat(calendar(emptyList(), today).contains("END:VCALENDAR"), "Calendário vazio")
        assertEquals("a\\\\b\\;c\\,d\\ne", escapeIcs("a\\b;c,d\ne"))
        val long = "Título" + "á".repeat(70)
        val folded = foldIcsLine("SUMMARY:" + long)
        assertThat(folded.contains("\r\n "), "Linha longa deve dobrar")
        assertEquals("SUMMARY:" + long, folded.replace("\r\n ", ""))
        assertThat(folded.split("\r\n").all { it.toByteArray(StandardCharsets.UTF_8).size <= 75 }, "Limite UTF-8")
        assertFails { calendar(listOf(Document("X\nY", today)), today) }
        assertFails { calendar(listOf(Document("X", today), Document("x", today)), today) }
    }

    private fun testPersistenceAndCli() {
        val directory = Files.createTempDirectory("documentos-test-")
        val file = File(directory.toFile(), "documentos.tsv")
        try {
            saveDocuments(file, fixture())
            assertEquals(fixture().sortedBy { it.date }, readDocuments(file))
            assertThat(executeCommand(arrayOf("listar", today.toString(), file.path)).contains("VENCIDO"), "Listagem CLI")
            assertThat(executeCommand(arrayOf("relatorio", today.toString(), file.path)).contains("Total: 4"), "Relatório CLI")
            assertThat(executeCommand(arrayOf("calendario", today.toString(), file.path)).contains("BEGIN:VCALENDAR"), "Calendário CLI")
            assertThat(executeCommand(arrayOf(today.toString(), file.path)).contains("BEGIN:VCALENDAR"), "Compatibilidade CLI")
            assertThat(executeCommand(arrayOf("listar", today.toString(), file.path, "--dias", "0")).contains("EM_DIA"), "Janela CLI")
            assertThat(executeCommand(arrayOf("adicionar", file.path, "RG", "2027-01-05", "Pessoal", "Atualizar", "3")).contains("adicionado"), "Adicionar CLI")
            assertEquals(5, readDocuments(file).size)
            assertThat(executeCommand(arrayOf("atualizar", file.path, "RG", "2027-01-05", "RG", "2027-01-06")).contains("atualizado"), "Atualizar CLI")
            assertThat(readDocuments(file).any { it.title == "RG" && it.date == LocalDate.parse("2027-01-06") }, "Persistência da atualização")
            assertThat(executeCommand(arrayOf("remover", file.path, "RG", "2027-01-06")).contains("removido"), "Remover CLI")
            assertEquals(4, readDocuments(file).size)
            assertFails { executeCommand(arrayOf("listar", today.toString(), file.path, "--dias", "-1")) }
            assertFails { executeCommand(arrayOf("comando-desconhecido")) }
            assertFails { executeCommand(arrayOf("remover", file.path, "Não existe", "2026-12-31")) }
            val before = file.readText()
            assertFails { executeCommand(arrayOf("adicionar", file.path, "Seguro", "2026-12-31")) }
            assertEquals(before, file.readText())
            assertFails { readDocuments(File(directory.toFile(), "ausente.tsv")) }
        } finally {
            file.delete()
            Files.deleteIfExists(directory)
        }
    }

    fun runAll() {
        testValidation()
        testTsv()
        testMutations()
        testStatusAndReport()
        testCalendar()
        testPersistenceAndCli()
        println("$checks verificações aprovadas")
    }
}
