package io.legado.app.server.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * 统一响应格式 {isSuccess, errorMsg, data}（与 legado 前端既有约定一致）
 */
suspend fun ApplicationCall.respondJson(
    data: Any?,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val payload = io.legado.app.utils.GSON.toJson(
        mapOf("isSuccess" to (status.isSuccess()), "errorMsg" to "", "data" to data)
    )
    respondText(payload, ContentType.Application.Json, status)
}

suspend fun ApplicationCall.respondError(status: Int, errorMsg: String) {
    val payload = io.legado.app.utils.GSON.toJson(
        mapOf("isSuccess" to false, "errorMsg" to errorMsg, "data" to null)
    )
    respondText(payload, ContentType.Application.Json, HttpStatusCode.fromValue(status))
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
