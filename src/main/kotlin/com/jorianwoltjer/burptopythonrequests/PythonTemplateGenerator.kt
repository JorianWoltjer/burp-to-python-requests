package com.jorianwoltjer.burptopythonrequests

import burp.api.montoya.http.message.HttpRequestResponse
import com.jorianwoltjer.burptopythonrequests.PythonRequestsConverter.Companion.IGNORED_HEADERS
import kotlin.collections.contains

class PythonTemplateGenerator() {
    companion object {
        private val AUTH_HEADERS = arrayOf(
            "authorization",
            "cookie"
        )

        fun generate(requestResponse: HttpRequestResponse): String {
            val request = requestResponse.request()

            val code = StringBuilder()
            code.append("import requests\n\n")
            code.append("HOST = \"")
            code.append(request.url().split('/').take(3).joinToString("/"))
            code.append("\"\n\n")
            code.append("s = requests.Session()\n")
            val headers = request.headers().filter { !IGNORED_HEADERS.contains(it.name().lowercase()) || AUTH_HEADERS.contains(it.name().lowercase()) }
            if (headers.isNotEmpty()) {
                code.append("s.headers.update({\n")
                headers.forEachIndexed { index, header ->
                    code.append("    \"")
                    code.append(header.name())
                    code.append("\": \"")
                    code.append(header.value().replace("\"", "\\\""))
                    code.append("\"")
                    if (index < headers.size - 1) {
                        code.append(",\n")
                    }
                }
                code.append("\n})\n")
            }
            code.append("\n")

            return code.toString()
        }
    }

}
