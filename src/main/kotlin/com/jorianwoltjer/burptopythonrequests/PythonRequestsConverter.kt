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

        fun convert(requestResponse: HttpRequestResponse): String {
            val request = requestResponse.request()
            val response = requestResponse.response()

            val code = StringBuilder()

            val data = request.parameters(HttpParameterType.BODY)
            if (data.isNotEmpty()) {
                code.append("data = {\n    ")
                data.forEachIndexed { index, param ->
                    code.append("    \"")
                    code.append(escape(param.name()))
                    code.append("\": \"")
                    code.append(escape(param.value()))
                    code.append("\"")
                    if (index < data.size - 1) {
                        code.append(",\n    ")
                    }
                }
                code.append("\n    }\n    ")
            }
            if (request.contentType().equals(ContentType.JSON)) {
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
                code.append("\n    ")
            }

            val headers = request.headers().filter { !IGNORED_HEADERS.contains(it.name().lowercase()) }
            if (headers.isNotEmpty()) {
                code.append("headers = {\n    ")
                headers.forEachIndexed { index, header ->
                    code.append("    \"")
                    code.append(escape(header.name()))
                    code.append("\": \"")
                    code.append(escape(header.value()))
                    code.append("\"")
                    if (index < headers.size - 1) {
                        code.append(",\n    ")
                    }
                }
                code.append("\n    }\n    ")
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
            if (data.isNotEmpty()) {
                code.append(", data=data")
            } else if (request.contentType().equals(ContentType.JSON)) {
                code.append(", json=data")
            }
            if (headers.isNotEmpty()) {
                code.append(", headers=headers")
            }

            code.append(")\n    ")
            code.append("assert r.ok, f\"{r.status_code}: {r.text}\"\n\n    ")
            if (response != null && response.mimeType().equals(MimeType.JSON)) {
                code.append("return r.json()\n")
            } else {
                code.append("return r.text\n")
            }

            return code.toString()
        }
    }
}
