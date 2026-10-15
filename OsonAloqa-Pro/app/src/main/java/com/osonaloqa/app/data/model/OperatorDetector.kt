package com.osonaloqa.app.data.model

object OperatorDetector {

    data class Operator(val name: String, val country: String)

    private val tj = mapOf(
        listOf("55", "77", "91", "92", "93", "94") to Operator("Tcell", "TJ"),
        listOf("88") to Operator("Megafon Tajikistan", "TJ"),
        listOf("40", "44", "50", "89") to Operator("ZET-Mobile (Babilon)", "TJ"),
        listOf("70", "80", "98", "995") to Operator("Tamom / UCell", "TJ"),
        listOf("17") to Operator("Beeline Tajikistan", "TJ")
    )

    private val ru = mapOf(
        listOf("910", "911", "912", "913", "914", "915", "916", "917", "918", "919", "980", "981", "982", "983", "984", "985", "986", "987", "988", "989") to Operator("МТС", "RU"),
        listOf("900", "901", "904", "905", "906", "909", "960", "961", "962", "963", "964", "965", "966", "967", "968") to Operator("Билайн", "RU"),
        listOf("920", "921", "922", "923", "924", "925", "926", "927", "928", "929", "930", "931", "932", "933", "938", "999") to Operator("МегаФон", "RU"),
        listOf("950", "951", "952", "953", "954", "958", "959", "969", "970", "971", "978", "979") to Operator("Tele2", "RU"),
        listOf("995", "996", "997") to Operator("Yota", "RU")
    )

    fun detect(e164: String): Operator {
        val digits = e164.filter { it.isDigit() }
        val national = when {
            digits.startsWith("992") -> digits.removePrefix("992")
            digits.startsWith("7") -> digits.removePrefix("7")
            else -> return Operator("—", "—")
        }
        val table = if (digits.startsWith("992")) tj else ru
        for ((prefixes, op) in table) {
            for (p in prefixes) {
                if (national.startsWith(p)) return op
            }
        }
        return Operator("Дигар оператор", if (digits.startsWith("992")) "TJ" else "RU")
    }

    fun supportedCountries(): List<String> = listOf("+992 Тоҷикистон", "+7 Россия")
}
