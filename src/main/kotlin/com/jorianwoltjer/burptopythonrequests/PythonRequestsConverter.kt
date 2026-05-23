package com.jorianwoltjer.burptopythonrequests

import burp.api.montoya.http.message.ContentType
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.MimeType
import burp.api.montoya.http.message.params.HttpParameterType
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.ObjectMapper


class PythonRequestsConverter {
    companion object {
        val IGNORED_HEADERS = arrayOf(
            "host",
            "content-length",
            "user-agent",
            "accept",
            "accept-encoding",
            "connection",
            "upgrade-insecure-requests",
            "content-type",
            "accept-language",
            "referer",
            "cookie",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "sec-fetch-dest",
            "sec-fetch-mode",
            "sec-fetch-site",
            "sec-fetch-user",
            "sec-fetch-storage-access",
            "priority",
            "if-modified-since",
            "if-none-match",
            "cache-control",
            "authorization",
            "cookie",
            "origin",
            "pragma"
        )

        private fun escape(input: String): String {
            val namedEscapes = mapOf(
                '\n' to "\\n",
                '\r' to "\\r",
                '\t' to "\\t",
                '\b' to "\\b",
                '\u000C' to "\\f",
                '\"' to "\\\"",
                '\'' to "\\'",
                '\\' to "\\\\"
            )

            val sb = StringBuilder()
            for (ch in input) {
                val code = ch.code
                sb.append(
                    when {
                        namedEscapes.containsKey(ch) -> namedEscapes[ch]
                        code in 0x20..0x7E -> ch  // printable ASCII
                        code <= 0xFF -> "\\x%02X".format(code)
                        code <= 0xFFFF -> "\\u%04X".format(code)
                        else -> "\\U%08X".format(code)
                    }
                )
            }
            return sb.toString()
        }

        private fun escapeBytes(input: ByteArray): String {
            val sb = StringBuilder("b\"")
            for (b in input) {
                val code = b.toInt() and 0xFF
                sb.append(
                    when (code) {
                        '\n'.code -> "\\n"
                        '\r'.code -> "\\r"
                        '\t'.code -> "\\t"
                        '\\'.code -> "\\\\"
                        '"'.code -> "\\\""
                        in 0x20..0x7E -> code.toChar()
                        else -> "\\x%02X".format(code)
                    }
                )
            }
            sb.append('"')
            return sb.toString()
        }

        private fun indentBody(code: String): String {
            val lines = code.lines()
            if (lines.isEmpty()) {
                return code
            }
            return buildString {
                append("    " + lines[0])
                for (i in 1 until lines.size) {
                    append('\n')
                    val line = lines[i]
                    append(if (line.isEmpty()) line else "    " + line)
                }
            }
        }

        private fun appendStringDict(code: StringBuilder, fields: List<MultipartParser.Field>) {
            code.append("data = {\n")
            fields.forEachIndexed { index, field ->
                code.append("    \"")
                code.append(escape(field.name))
                code.append("\": \"")
                code.append(escape(field.value))
                code.append("\"")
                if (index < fields.size - 1) {
                    code.append(",\n")
                }
            }
            code.append("\n}\n")
        }

        private fun appendFilesDict(code: StringBuilder, files: List<MultipartParser.File>) {
            code.append("files = {\n")
            files.forEachIndexed { index, file ->
                code.append("    \"")
                code.append(escape(file.name))
                code.append("\": (\"")
                code.append(escape(file.filename))
                code.append("\", ")
                code.append(escapeBytes(file.content))
                if (!file.contentType.isNullOrBlank()) {
                    code.append(", \"")
                    code.append(escape(file.contentType))
                    code.append("\"")
                }
                code.append(")")
                if (index < files.size - 1) {
                    code.append(",\n")
                }
            }
            code.append("\n}\n")
        }

        fun convert(requestResponse: HttpRequestResponse): String {
            val request = requestResponse.request()
            val response = requestResponse.response()

            val code = StringBuilder()

            var hasData = false
            var hasFiles = false
            var hasJson = false

            when {
                request.contentType().equals(ContentType.MULTIPART) -> {
                    val multipart = MultipartParser.parse(request)
                    if (multipart.fields.isNotEmpty()) {
                        appendStringDict(code, multipart.fields)
                        hasData = true
                    }
                    if (multipart.files.isNotEmpty()) {
                        appendFilesDict(code, multipart.files)
                        hasFiles = true
                    }
                }
                request.contentType().equals(ContentType.JSON) -> {
                    code.append("data = ")
                    val objectMapper = ObjectMapper()
                    val jsonObject = objectMapper.readTree(request.bodyToString())
                    val prettyPrinter = DefaultPrettyPrinter().withSeparators(
                        Separators.createDefaultInstance().withArrayValueSpacing(Separators.Spacing.AFTER)
                            .withObjectEntrySpacing(Separators.Spacing.AFTER)
                            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                    )
                    prettyPrinter.indentObjectsWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
                    prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
                    var json = objectMapper.writer(prettyPrinter).writeValueAsString(jsonObject)
                    json = json.replace("\n", "\n    ")
                    code.append(json)
                    code.append("\n")
                    hasJson = true
                }
                else -> {
                    val data = request.parameters(HttpParameterType.BODY)
                    if (data.isNotEmpty()) {
                        appendStringDict(code, data.map { MultipartParser.Field(it.name(), it.value()) })
                        hasData = true
                    }
                }
            }

            val headers = request.headers().filter { !IGNORED_HEADERS.contains(it.name().lowercase()) }
            if (headers.isNotEmpty()) {
                code.append("headers = {\n")
                headers.forEachIndexed { index, header ->
                    code.append("    \"")
                    code.append(escape(header.name()))
                    code.append("\": \"")
                    code.append(escape(header.value()))
                    code.append("\"")
                    if (index < headers.size - 1) {
                        code.append(",\n")
                    }
                }
                code.append("\n}\n")
            }

            code.append("r = s.")
            code.append(request.method().lowercase())
            code.append("(HOST + \"")
            code.append(escape(request.pathWithoutQuery()))
            code.append("\"")

            val params = request.parameters(HttpParameterType.URL)
            if (params.isNotEmpty()) {
                code.append(", params={")
                params.forEachIndexed { index, param ->
                    code.append("\"")
                    code.append(escape(param.name()))
                    code.append("\": \"")
                    code.append(escape(param.value()))
                    code.append("\"")
                    if (index < params.size - 1) {
                        code.append(", ")
                    }
                }
                code.append("}")
            }
            if (hasData) {
                code.append(", data=data")
            } else if (hasJson) {
                code.append(", json=data")
            }
            if (hasFiles) {
                code.append(", files=files")
            }
            if (headers.isNotEmpty()) {
                code.append(", headers=headers")
            }

            code.append(")\n")
            code.append("assert r.ok, f\"{r.status_code}: {r.text}\"\n\n")
            if (response != null && response.mimeType().equals(MimeType.JSON)) {
                code.append("return r.json()\n")
            } else {
                code.append("return r.text\n")
            }

            return indentBody(code.toString())
        }
    }
}
