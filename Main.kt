import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
data class Document(val title: String, val date: LocalDate)
fun escapeIcs(text: String): String = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
fun calendar(documents: List<Document>, today: LocalDate): String {
    require(documents.all { it.title.isNotBlank() && it.title.length <= 40 && it.title.all { c -> c.code in 32..126 } }) { "Nesta versão o título deve ter 1 a 40 caracteres ASCII" }
    val fmt=DateTimeFormatter.BASIC_ISO_DATE
    val lines=mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Documentos Validade//PT-BR","CALSCALE:GREGORIAN")
    for (doc in documents.sortedBy { it.date }) {
        val uid=UUID.nameUUIDFromBytes("${doc.title}|${doc.date}".toByteArray(Charsets.UTF_8))
        lines += listOf("BEGIN:VEVENT","UID:$uid@documentos-validade.local","DTSTAMP:${today.format(fmt)}T000000Z","DTSTART;VALUE=DATE:${doc.date.format(fmt)}","DTEND;VALUE=DATE:${doc.date.plusDays(1).format(fmt)}","SUMMARY:${escapeIcs(doc.title)}","END:VEVENT")
    }
    lines += "END:VCALENDAR"; return lines.joinToString("\r\n", postfix="\r\n")
}
fun main(args: Array<String>) {
    try {
        if (args.contentEquals(arrayOf("--self-test"))) {
            val day=LocalDate.parse("2026-12-31")
            val out=calendar(listOf(Document("CNH; renovar",day)),day)
            check(out.contains("DTEND;VALUE=DATE:20270101\r\n")); check(out.contains("SUMMARY:CNH\\; renovar"))
            check(runCatching { calendar(listOf(Document("x\nBEGIN:VEVENT",day)),day) }.isFailure)
            println("3 cenários OK"); return
        }
        require(args.size==2) { "Uso: java -jar app.jar DATA_ATUAL documentos.tsv > validade.ics" }
        val rows=File(args[1]).readLines().filter { it.isNotBlank() }; require(rows.firstOrNull()=="titulo\tvalidade")
        val docs=rows.drop(1).map { val c=it.split('\t'); require(c.size==2); Document(c[0],LocalDate.parse(c[1])) }
        print(calendar(docs,LocalDate.parse(args[0])))
    } catch(e:Exception) { System.err.println("Erro: ${e.message}"); kotlin.system.exitProcess(2) }
}
