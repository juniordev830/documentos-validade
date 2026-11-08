import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val MAX_DOCUMENTS = 10_000
private const val MAX_FILE_BYTES = 2_000_000L
private const val HEADER = "titulo\tvalidade\tcategoria\tnotas\tlembrete_dias"

/** A due date and its optional grouping, notes and advance reminder. */
data class Document(
    val title: String,
    val date: LocalDate,
    val category: String = "Geral",
    val notes: String = "",
    val reminderDays: Int = 0
) {
    fun validated(): Document {
        validateText(title, "Título", 80, allowEmpty = false)
        validateText(category, "Categoria", 40, allowEmpty = false)
        validateText(notes, "Notas", 200, allowEmpty = true)
        require(date.year in 1900..9998) { "Ano de validade fora do intervalo permitido" }
        require(reminderDays in 0..365) { "Lembrete deve estar entre 0 e 365 dias" }
        return this
    }
}

private fun validateText(value: String, field: String, maximum: Int, allowEmpty: Boolean) {
    require(allowEmpty || value.isNotBlank()) { "$field não pode estar vazio" }
    require(value.length <= maximum) { "$field excede $maximum caracteres" }
    require(value.none { it.isISOControl() }) { "$field contém caracteres de controle" }
}

/** Case-insensitive title/date key prevents accidental duplicate reminders. */
fun documentKey(document: Document): String =
    "${document.title.trim().lowercase(java.util.Locale.ROOT)}|${document.date}"

fun validateCollection(documents: List<Document>): List<Document> {
    require(documents.size <= MAX_DOCUMENTS) { "Limite de $MAX_DOCUMENTS documentos excedido" }
    documents.forEach(Document::validated)
    require(documents.map(::documentKey).distinct().size == documents.size) {
        "Existem documentos duplicados com mesmo título e validade"
    }
    return documents
}

/** Accepts the original two-column TSV and an extended five-column format. */
fun parseDocuments(tsv: String): List<Document> {
    val lines = tsv.removePrefix("\uFEFF").lineSequence()
        .map { it.removeSuffix("\r") }
        .filter { it.isNotBlank() }
        .toList()
    require(lines.isNotEmpty()) { "Arquivo TSV vazio" }
    val header = lines.first().split('\t')
    require(header == listOf("titulo", "validade") ||
        header == listOf("titulo", "validade", "categoria", "notas", "lembrete_dias")) {
        "Cabeçalho TSV inválido"
    }
    require(lines.size - 1 <= MAX_DOCUMENTS) { "Limite de documentos excedido" }
    val documents = lines.drop(1).mapIndexed { index, line ->
        val cells = line.split('\t')
        require(cells.size == header.size) { "Linha ${index + 2}: quantidade de colunas inválida" }
        try {
            Document(
                title = cells[0],
                date = LocalDate.parse(cells[1]),
                category = if (cells.size == 5) cells[2] else "Geral",
                notes = if (cells.size == 5) cells[3] else "",
                reminderDays = if (cells.size == 5) cells[4].toInt() else 0
            ).validated()
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Linha ${index + 2}: ${error.message}", error)
        }
    }
    return validateCollection(documents)
}

fun serializeDocuments(documents: List<Document>): String {
    validateCollection(documents)
    val rows = documents.sortedWith(compareBy<Document> { it.date }.thenBy { it.title })
        .map { listOf(it.title, it.date.toString(), it.category, it.notes, it.reminderDays.toString()).joinToString("\t") }
    return (listOf(HEADER) + rows).joinToString("\n", postfix = "\n")
}

fun readDocuments(file: File): List<Document> {
    require(file.isFile) { "Arquivo não encontrado: ${file.path}" }
    require(file.length() <= MAX_FILE_BYTES) { "Arquivo TSV excede 2 MB" }
    return parseDocuments(file.readText(StandardCharsets.UTF_8))
}

/** Replace an existing TSV only after successful validation and temporary write. */
fun saveDocuments(file: File, documents: List<Document>) {
    val text = serializeDocuments(documents)
    val target = file.toPath().toAbsolutePath()
    val directory = target.parent
    require(Files.isDirectory(directory)) { "Diretório de destino inexistente" }
    val temporary = Files.createTempFile(directory, ".documentos-", ".tmp")
    try {
        Files.writeString(temporary, text, StandardCharsets.UTF_8)
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

fun addDocument(documents: List<Document>, document: Document): List<Document> =
    validateCollection(documents + document.validated())

fun removeDocument(documents: List<Document>, title: String, date: LocalDate): List<Document> {
    val key = documentKey(Document(title, date))
    require(documents.any { documentKey(it) == key }) { "Documento não encontrado" }
    return documents.filterNot { documentKey(it) == key }
}

fun updateDocument(
    documents: List<Document>, title: String, originalDate: LocalDate, replacement: Document
): List<Document> {
    val key = documentKey(Document(title, originalDate))
    require(documents.any { documentKey(it) == key }) { "Documento não encontrado" }
    return validateCollection(documents.map { if (documentKey(it) == key) replacement else it })
}

enum class ExpiryStatus { VENCIDO, VENCE_HOJE, PROXIMO, EM_DIA }

data class DocumentOverview(val document: Document, val daysRemaining: Long, val status: ExpiryStatus)

fun classifyDocument(document: Document, today: LocalDate, upcomingDays: Int = 30): DocumentOverview {
    require(upcomingDays in 0..3650) { "Janela de dias deve estar entre 0 e 3650" }
    val remaining = ChronoUnit.DAYS.between(today, document.date)
    val status = when {
        remaining < 0 -> ExpiryStatus.VENCIDO
        remaining == 0L -> ExpiryStatus.VENCE_HOJE
        remaining <= upcomingDays -> ExpiryStatus.PROXIMO
        else -> ExpiryStatus.EM_DIA
    }
    return DocumentOverview(document, remaining, status)
}

fun listDocuments(
    documents: List<Document>, today: LocalDate, upcomingDays: Int = 30,
    category: String? = null, onlyAttention: Boolean = false
): List<DocumentOverview> {
    validateCollection(documents)
    require(upcomingDays in 0..3650) { "Janela de dias inválida" }
    return documents.asSequence()
        .filter { category == null || it.category.equals(category, ignoreCase = true) }
        .map { classifyDocument(it, today, upcomingDays) }
        .filter { !onlyAttention || it.status != ExpiryStatus.EM_DIA }
        .sortedWith(compareBy<DocumentOverview> { it.document.date }.thenBy { it.document.title })
        .toList()
}

data class ExpiryReport(
    val total: Int,
    val expired: Int,
    val dueToday: Int,
    val upcoming: Int,
    val valid: Int,
    val byCategory: Map<String, Int>
)

fun buildReport(documents: List<Document>, today: LocalDate, upcomingDays: Int = 30): ExpiryReport {
    val overviews = listDocuments(documents, today, upcomingDays)
    val counts = overviews.groupingBy { it.status }.eachCount()
    return ExpiryReport(
        total = overviews.size,
        expired = counts[ExpiryStatus.VENCIDO] ?: 0,
        dueToday = counts[ExpiryStatus.VENCE_HOJE] ?: 0,
        upcoming = counts[ExpiryStatus.PROXIMO] ?: 0,
        valid = counts[ExpiryStatus.EM_DIA] ?: 0,
        byCategory = documents.groupingBy { it.category }.eachCount().toSortedMap()
    )
}

/** Escapes RFC 5545 TEXT values, including commas and line breaks. */
fun escapeIcs(text: String): String = text.replace("\\", "\\\\")
    .replace(";", "\\;").replace(",", "\\,")
    .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

/** Folds iCalendar content lines at 75 UTF-8 octets without splitting a character. */
fun foldIcsLine(line: String): String {
    val result = StringBuilder()
    var octets = 0
    var offset = 0
    while (offset < line.length) {
        val codePoint = line.codePointAt(offset)
        val value = String(Character.toChars(codePoint))
        val bytes = value.toByteArray(StandardCharsets.UTF_8).size
        if (octets + bytes > 75) {
            result.append("\r\n ")
            octets = 1
        }
        result.append(value)
        octets += bytes
        offset += Character.charCount(codePoint)
    }
    return result.toString()
}

fun calendar(documents: List<Document>, today: LocalDate): String {
    validateCollection(documents)
    val fmt = DateTimeFormatter.BASIC_ISO_DATE
    val lines = mutableListOf(
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Documentos Validade//PT-BR", "CALSCALE:GREGORIAN"
    )
    for (document in documents.sortedWith(compareBy<Document> { it.date }.thenBy { it.title })) {
        val key = "${document.title}|${document.date}" +
            if (document.category == "Geral") "" else "|${document.category}"
        val uid = UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8))
        lines += "BEGIN:VEVENT"
        lines += "UID:$uid@documentos-validade.local"
        lines += "DTSTAMP:${today.format(fmt)}T000000Z"
        lines += "DTSTART;VALUE=DATE:${document.date.format(fmt)}"
        lines += "DTEND;VALUE=DATE:${document.date.plusDays(1).format(fmt)}"
        lines += "SUMMARY:${escapeIcs(document.title)}"
        if (document.category != "Geral") lines += "CATEGORIES:${escapeIcs(document.category)}"
        if (document.notes.isNotEmpty()) lines += "DESCRIPTION:${escapeIcs(document.notes)}"
        if (document.reminderDays > 0) {
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${escapeIcs(document.title)}"
            lines += "TRIGGER:-P${document.reminderDays}D"
            lines += "END:VALARM"
        }
        lines += "END:VEVENT"
    }
    lines += "END:VCALENDAR"
    return lines.joinToString("\r\n") { foldIcsLine(it) } + "\r\n"
}

private fun parseDay(value: String): LocalDate = LocalDate.parse(value)

private fun optionalDays(args: Array<String>, index: Int): Int {
    if (args.size == index) return 30
    require(args.size == index + 2 && args[index] == "--dias") { "Use --dias N" }
    return args[index + 1].toInt().also { require(it in 0..3650) }
}

private fun documentFromArgs(args: Array<String>, start: Int): Document {
    require(args.size in (start + 2)..(start + 5)) { "Informe título, data e opcionais" }
    return Document(
        args[start], parseDay(args[start + 1]),
        args.getOrNull(start + 2) ?: "Geral",
        args.getOrNull(start + 3) ?: "",
        args.getOrNull(start + 4)?.toInt() ?: 0
    ).validated()
}

fun executeCommand(args: Array<String>): String {
    require(args.isNotEmpty()) { "Informe um comando; veja README.md" }
    if (args.size == 2 && runCatching { parseDay(args[0]) }.isSuccess) {
        return calendar(readDocuments(File(args[1])), parseDay(args[0]))
    }
    return when (args[0]) {
        "calendario" -> {
            require(args.size == 3) { "Uso: calendario DATA arquivo.tsv" }
            calendar(readDocuments(File(args[2])), parseDay(args[1]))
        }
        "listar" -> {
            require(args.size >= 3) { "Uso: listar DATA arquivo.tsv [--dias N]" }
            val today = parseDay(args[1])
            val rows = listDocuments(readDocuments(File(args[2])), today, optionalDays(args, 3))
            (listOf("titulo\tvalidade\tcategoria\tdias_restantes\tstatus") + rows.map {
                "${it.document.title}\t${it.document.date}\t${it.document.category}\t${it.daysRemaining}\t${it.status}"
            }).joinToString("\n", postfix = "\n")
        }
        "relatorio" -> {
            require(args.size >= 3) { "Uso: relatorio DATA arquivo.tsv [--dias N]" }
            val report = buildReport(readDocuments(File(args[2])), parseDay(args[1]), optionalDays(args, 3))
            buildString {
                appendLine("Total: ${report.total}")
                appendLine("Vencidos: ${report.expired}")
                appendLine("Vencem hoje: ${report.dueToday}")
                appendLine("Próximos: ${report.upcoming}")
                appendLine("Em dia: ${report.valid}")
                report.byCategory.forEach { (category, count) -> appendLine("$category: $count") }
            }
        }
        "adicionar" -> {
            require(args.size >= 4) { "Uso: adicionar arquivo.tsv TITULO DATA [CATEGORIA] [NOTAS] [LEMBRETE]" }
            val file = File(args[1])
            val current = if (file.exists()) readDocuments(file) else emptyList()
            saveDocuments(file, addDocument(current, documentFromArgs(args, 2)))
            "Documento adicionado\n"
        }
        "remover" -> {
            require(args.size == 4) { "Uso: remover arquivo.tsv TITULO DATA" }
            val file = File(args[1])
            saveDocuments(file, removeDocument(readDocuments(file), args[2], parseDay(args[3])))
            "Documento removido\n"
        }
        "atualizar" -> {
            require(args.size >= 6) { "Uso: atualizar arquivo.tsv TITULO DATA_ANTIGA NOVO_TITULO NOVA_DATA [CATEGORIA] [NOTAS] [LEMBRETE]" }
            val file = File(args[1])
            val replacement = documentFromArgs(args, 4)
            saveDocuments(file, updateDocument(readDocuments(file), args[2], parseDay(args[3]), replacement))
            "Documento atualizado\n"
        }
        else -> throw IllegalArgumentException("Comando desconhecido: ${args[0]}")
    }
}

fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("--self-test"))) {
            DocumentTests.runAll()
            return
        }
        print(executeCommand(args))
    } catch (error: Exception) {
        System.err.println("Erro: ${error.message}")
        kotlin.system.exitProcess(2)
    }
}
