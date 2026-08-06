package vad.dashing.tbox.ui.launcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Small dependency-free structural validator for the bundled GLB and its pivot contract JSON. */
object LauncherCarAssetStructure {
    private const val GLB_MAGIC = 0x46546C67
    private const val GLB_VERSION_2 = 2
    private const val JSON_CHUNK = 0x4E4F534A

    data class Result(
        val glbNodeNames: Set<String>,
        val missingGlbNodes: Set<String>,
        val missingPivotReferences: Set<String>,
    ) {
        val isValid: Boolean
            get() = missingGlbNodes.isEmpty() && missingPivotReferences.isEmpty()
    }

    fun validate(glbBytes: ByteArray, pivotJson: String): Result {
        val glbJson = extractGlbJson(glbBytes)
        val nodesJson = extractJsonArray(glbJson, "nodes")
        val nodeNames = Regex(""""name"\s*:\s*"([^"]+)"""")
            .findAll(nodesJson)
            .map { it.groupValues[1] }
            .toSet()
        val required = LauncherCarRigController.requiredNodeNames
        return Result(
            glbNodeNames = nodeNames,
            missingGlbNodes = required - nodeNames,
            missingPivotReferences = required.filterNot { requiredName ->
                Regex(""""${Regex.escape(requiredName)}"""").containsMatchIn(pivotJson)
            }.toSet(),
        )
    }

    fun extractGlbJson(bytes: ByteArray): String {
        require(bytes.size >= 20) { "GLB is shorter than its header and first chunk" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.int == GLB_MAGIC) { "Invalid GLB magic" }
        require(buffer.int == GLB_VERSION_2) { "Only GLB v2 is supported" }
        val declaredLength = buffer.int
        require(declaredLength in 20..bytes.size) { "Invalid GLB declared length" }

        while (buffer.position() + 8 <= declaredLength) {
            val chunkLength = buffer.int
            val chunkType = buffer.int
            require(chunkLength >= 0 && buffer.position() + chunkLength <= declaredLength) {
                "Invalid GLB chunk length"
            }
            if (chunkType == JSON_CHUNK) {
                val jsonBytes = ByteArray(chunkLength)
                buffer.get(jsonBytes)
                return jsonBytes.toString(Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t')
            }
            buffer.position(buffer.position() + chunkLength)
        }
        error("GLB JSON chunk is missing")
    }

    internal fun extractJsonArray(json: String, property: String): String {
        val propertyIndex = json.indexOf("\"$property\"")
        require(propertyIndex >= 0) { "JSON property '$property' is missing" }
        val arrayStart = json.indexOf('[', propertyIndex)
        require(arrayStart >= 0) { "JSON property '$property' is not an array" }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in arrayStart until json.length) {
            val char = json[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return json.substring(arrayStart, index + 1)
                    }
                }
            }
        }
        error("JSON array '$property' is not closed")
    }
}
