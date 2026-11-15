import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
data class Document(val title: String, val date: LocalDate)
fun escapeIcs(text: String): String = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
fun calendar(documents: List<Document>, today: LocalDate): String {
    require(documents.all { it.title.isNotBlank() && it.title.length <= 30 && it.title.all { c -> c.code in 32..126 } }) { "Nesta versão o título deve ter 1 a 30 caracteres ASCII" }
    val fmt=DateTimeFormatter.BASIC_ISO_DATE
    val lines=mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Documentos Validade//PT-BR","CALSCALE:GREGORIAN")
    for (doc in documents.sortedBy { it.date }) {
        val uid=UUID.nameUUIDFromBytes("${doc.title}|${doc.date}".toByteArray(Charsets.UTF_8))
        lines += listOf("BEGIN:VEVENT","UID:$uid@documentos-validade.local","DTSTAMP:${today.format(fmt)}T000000Z","DTSTART;VALUE=DATE:${doc.date.format(fmt)}","DTEND;VALUE=DATE:${doc.date.plusDays(1).format(fmt)}","SUMMARY:${escapeIcs(doc.title)}","END:VEVENT")
    }
    lines += "END:VCALENDAR"; return lines.joinToString("\r\n", postfix="\r\n")
}
fun upcoming(documents: List<Document>, today: LocalDate, windowDays: Int): List<Document> {
    require(windowDays in 0..3650) { "A janela deve estar entre 0 e 3650 dias" }
    val end = try { today.plusDays(windowDays.toLong()) } catch (e: java.time.DateTimeException) {
        throw IllegalArgumentException("Janela fora do limite do calendário", e)
    }
    return documents.filter { !it.date.isBefore(today) && !it.date.isAfter(end) }
        .sortedWith(compareBy<Document> { it.date }.thenBy { it.title })
}
fun readDocuments(path: String): List<Document> {
    val rows = File(path).readLines().filter { it.isNotBlank() }
    require(rows.firstOrNull() == "titulo\tvalidade") { "Cabeçalho TSV inválido" }
    return rows.drop(1).map { row ->
        val cells = row.split('\t')
        require(cells.size == 2 && cells[0].isNotBlank()) { "Documento TSV inválido" }
        Document(cells[0], LocalDate.parse(cells[1]))
    }
}
fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("--self-test"))) {
            val day=LocalDate.parse("2026-12-31")
            val out=calendar(listOf(Document("CNH; renovar",day)),day)
            check(out.contains("DTEND;VALUE=DATE:20270101\r\n")); check(out.contains("SUMMARY:CNH\\; renovar"))
            check(runCatching { calendar(listOf(Document("x\nBEGIN:VEVENT",day)),day) }.isFailure)
            val docs = listOf(Document("Fora",day.plusDays(8)),Document("Hoje",day),Document("Limite",day.plusDays(7)),Document("Vencido",day.minusDays(1)))
            check(upcoming(docs,day,7).map { it.title } == listOf("Hoje","Limite"))
            check(upcoming(docs,day,0).map { it.title } == listOf("Hoje"))
            check(upcoming(emptyList(),day,7).isEmpty())
            check(runCatching { upcoming(docs,day,-1) }.isFailure)
            check(runCatching { upcoming(docs,day,3651) }.isFailure)
            check(runCatching { upcoming(docs,LocalDate.MAX,1) }.isFailure)
            check(upcoming(listOf(Document("B",day),Document("A",day)),day,0).map { it.title } == listOf("A","B"))
            println("10 cenários OK"); return
        }
        if (args.firstOrNull() == "--proximos") {
            require(args.size == 4) { "Uso: java -jar app.jar --proximos DATA_ATUAL DIAS documentos.tsv" }
            val selected = upcoming(readDocuments(args[3]), LocalDate.parse(args[1]), args[2].toInt())
            println("titulo\tvalidade")
            for (doc in selected) println("${doc.title}\t${doc.date}")
        } else {
            require(args.size==2) { "Uso: java -jar app.jar DATA_ATUAL documentos.tsv > validade.ics" }
            print(calendar(readDocuments(args[1]),LocalDate.parse(args[0])))
        }
    } catch(e:Exception) { System.err.println("Erro: ${e.message}"); kotlin.system.exitProcess(2) }
}

