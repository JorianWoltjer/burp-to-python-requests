package com.jorianwoltjer.burptopythonrequests

import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import burp.api.montoya.http.message.requests.HttpRequest

class MultipartParser {
    data class Field(val name: String, val value: String)

    data class File(
        val name: String,
        val filename: String,
        val content: ByteArray,
        val contentType: String?,
    )

    data class Result(val fields: List<Field>, val files: List<File>)

    companion object {
        fun parse(request: HttpRequest): Result {
            val bodyParams = request.parameters(HttpParameterType.BODY)
            if (bodyParams.isEmpty()) {
                return Result(emptyList(), emptyList())
            }

            val fileNames = request.parameters(HttpParameterType.MULTIPART_ATTRIBUTE)
                .associate { it.name() to it.value() }
            val requestBytes = request.toByteArray().getBytes()

            val fields = bodyParams
                .filter { !isFilePart(it, fileNames, requestBytes) }
                .map { Field(it.name(), it.value()) }
            val files = bodyParams
                .filter { isFilePart(it, fileNames, requestBytes) }
                .map { toFile(it, fileNames, requestBytes) }

            return Result(fields, files)
        }

        private fun toFile(param: ParsedHttpParameter, fileNames: Map<String, String>, requestBytes: ByteArray): File {
            val nameStart = param.nameOffsets().startIndexInclusive()
            val valueStart = param.valueOffsets().startIndexInclusive()
            val valueEnd = param.valueOffsets().endIndexExclusive()
            return File(
                name = param.name(),
                filename = fileNames[param.name()] ?: getPartFilename(requestBytes, nameStart, valueStart) ?: "file",
                content = requestBytes.copyOfRange(valueStart, valueEnd),
                contentType = getPartContentType(requestBytes, nameStart, valueStart),
            )
        }

        private fun partHeaderSection(requestBytes: ByteArray, nameStart: Int, valueStart: Int): String {
            val searchStart = maxOf(0, minOf(nameStart, valueStart))
            return String(requestBytes, searchStart, valueStart - searchStart, Charsets.ISO_8859_1)
        }

        private fun getPartContentType(requestBytes: ByteArray, nameStart: Int, valueStart: Int): String? {
            return Regex("Content-Type:\\s*([^\\r\\n;]+)", RegexOption.IGNORE_CASE)
                .find(partHeaderSection(requestBytes, nameStart, valueStart))
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        private fun getPartFilename(requestBytes: ByteArray, nameStart: Int, valueStart: Int): String? {
            val headerSection = partHeaderSection(requestBytes, nameStart, valueStart)
            return Regex("filename\\*=[^;\\r\\n]+''([^;\\r\\n]+)", RegexOption.IGNORE_CASE)
                .findAll(headerSection)
                .lastOrNull()
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: Regex("filename=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                    .findAll(headerSection)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                ?: Regex("filename=([^\\r\\n;]+)", RegexOption.IGNORE_CASE)
                    .findAll(headerSection)
                    .lastOrNull()
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.trim('"')
        }

        private fun isFilePart(param: ParsedHttpParameter, fileNames: Map<String, String>, requestBytes: ByteArray): Boolean {
            if (fileNames.containsKey(param.name())) {
                return true
            }
            val valueStart = param.valueOffsets().startIndexInclusive()
            return getPartFilename(requestBytes, param.nameOffsets().startIndexInclusive(), valueStart) != null
        }
    }
}
