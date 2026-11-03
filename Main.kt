import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
data class Document(val title: String, val date: LocalDate)
fun escapeIcs(text: String): String = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
fun calendar(documents: List<Document>, today: LocalDate, alertDays: Int? = null): String {
    require(alertDays == null || alertDays in 0..365) { "Alerta deve estar entre 0 e 365 dias" }
    require(documents.all { it.title.isNotBlank() && it.title.length <= 30 && it.title.all { c -> c.code in 32..126 } }) { "Nesta versão o título deve ter 1 a 30 caracteres ASCII" }
    val fmt=DateTimeFormatter.BASIC_ISO_DATE
    val lines=mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Documentos Validade//PT-BR","CALSCALE:GREGORIAN")
    for (doc in documents.sortedBy { it.date }) {
        val uid=UUID.nameUUIDFromBytes("${doc.title}|${doc.date}".toByteArray(Charsets.UTF_8))
        lines += listOf("BEGIN:VEVENT","UID:$uid@documentos-validade.local",
            "DTSTAMP:${today.format(fmt)}T000000Z",
            "DTSTART;VALUE=DATE:${doc.date.format(fmt)}",
            "DTEND;VALUE=DATE:${doc.date.plusDays(1).format(fmt)}",
            "SUMMARY:${escapeIcs(doc.title)}")
        if (alertDays != null) {
            val trigger = if (alertDays == 0) "PT0S" else "-P${alertDays}D"
            lines += listOf("BEGIN:VALARM", "ACTION:DISPLAY",
                "DESCRIPTION:${escapeIcs("Vencimento: ${doc.title}")}",
                "TRIGGER;RELATED=START:$trigger", "END:VALARM")
        }
        lines += "END:VEVENT"
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
            val alarm = calendar(listOf(Document("CNH; renovar",day)),day,7)
            check(alarm.contains("BEGIN:VALARM\r\n"))
            check(alarm.contains("TRIGGER;RELATED=START:-P7D\r\n"))
            check(alarm.contains("DESCRIPTION:Vencimento: CNH\\; renovar\r\n"))
            check(!out.contains("BEGIN:VALARM"))
            check(calendar(listOf(Document("RG",day)),day,0).contains("TRIGGER;RELATED=START:PT0S"))
            check(runCatching { calendar(emptyList(),day,-1) }.isFailure)
            check(runCatching { calendar(emptyList(),day,366) }.isFailure)
            println("9 cenários OK"); return
        }
        require(args.size==2 || (args.size==4 && args[2]=="--alerta-dias")) {
            "Uso: java -jar app.jar DATA_ATUAL documentos.tsv [--alerta-dias 0..365] > validade.ics"
        }
        val alertDays = if (args.size==4) args[3].toInt() else null
        val rows=File(args[1]).readLines().filter { it.isNotBlank() }; require(rows.firstOrNull()=="titulo\tvalidade")
        val docs=rows.drop(1).map { val c=it.split('\t'); require(c.size==2); Document(c[0],LocalDate.parse(c[1])) }
        print(calendar(docs,LocalDate.parse(args[0]),alertDays))
    } catch(e:Exception) { System.err.println("Erro: ${e.message}"); kotlin.system.exitProcess(2) }
}
