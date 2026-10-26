package com.tawasol.app.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Sanitizes technical network exceptions and maps them into user-friendly Arabic messages.
 * Prevents raw IP addresses, URLs, port numbers, HTTP status codes, and stack traces
 * from leaking into user-facing UI while providing clear guidance on what went wrong.
 */
object NetworkErrorMapper {

    private const val DEFAULT_NETWORK_ERROR = "لا يوجد اتصال بالإنترنت. تحقق من اتصالك وحاول مرة أخرى"
    private const val TIMEOUT_ERROR = "انتهت مهلة الاتصال بالخادم. تحقق من سرعة اتصالك وحاول مرة أخرى"
    private const val SERVER_UNAVAILABLE_ERROR = "خادم تواصل غير متاح حالياً. يرجى المحاولة بعد قليل"
    private const val INVALID_CREDENTIALS_ERROR = "اسم المستخدم أو كلمة المرور غير صحيحة"
    private const val UNEXPECTED_ERROR = "حدث خطأ غير متوقع. يرجى المحاولة مرة أخرى"

    fun toUserMessage(throwable: Throwable?): String {
        if (throwable == null) return UNEXPECTED_ERROR

        val message = throwable.message.orEmpty()
        val cause = throwable.cause

        // 1. Check for invalid credentials
        if (message.contains("Invalid login credentials", ignoreCase = true) ||
            message.contains("invalid_credentials", ignoreCase = true) ||
            message.contains("user_not_found", ignoreCase = true)
        ) {
            return INVALID_CREDENTIALS_ERROR
        }

        // 2. Check for connection timeouts
        if (throwable is SocketTimeoutException ||
            cause is SocketTimeoutException ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
        ) {
            return TIMEOUT_ERROR
        }

        // 3. Check for DNS resolution or connection failures (including IP/port connection errors)
        if (throwable is UnknownHostException ||
            cause is UnknownHostException ||
            throwable is ConnectException ||
            cause is ConnectException ||
            throwable is NoRouteToHostException ||
            throwable is PortUnreachableException ||
            throwable is SSLException ||
            message.contains("Failed to connect", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("Network is unreachable", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true) ||
            message.contains("UnresolvedAddressException", ignoreCase = true)
        ) {
            return DEFAULT_NETWORK_ERROR
        }

        // 4. Server-side HTTP errors
        if (message.contains("500") ||
            message.contains("502") ||
            message.contains("503") ||
            message.contains("504") ||
            message.contains("Bad Gateway", ignoreCase = true) ||
            message.contains("Service Unavailable", ignoreCase = true)
        ) {
            return SERVER_UNAVAILABLE_ERROR
        }

        // 5. Clean custom domain exceptions that don't leak technical details
        if (throwable is IllegalArgumentException || throwable is IllegalStateException) {
            val rawMsg = throwable.localizedMessage ?: throwable.message ?: ""
            // Strip any IP addresses or raw domain URLs from user message
            val sanitized = sanitizeTechnicalDetails(rawMsg)
            if (sanitized.isNotBlank() && !sanitized.contains("Exception")) {
                return sanitized
            }
        }

        // 6. Generic safe fallback
        val sanitizedMsg = sanitizeTechnicalDetails(message)
        return if (sanitizedMsg.isNotBlank() && !sanitizedMsg.contains("Exception") && !sanitizedMsg.contains("supabase.co")) {
            sanitizedMsg
        } else {
            DEFAULT_NETWORK_ERROR
        }
    }

    /**
     * Removes IP addresses, ports, and internal domain names from error strings
     * to prevent technical leakage in the UI.
     */
    private fun sanitizeTechnicalDetails(raw: String): String {
        return raw
            // Remove IP:Port patterns (e.g. 104.18.38.10:443)
            .replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?\b"""), "")
            // Remove supabase.co URLs
            .replace(Regex("""https?://[a-zA-Z0-9.-]+\.supabase\.co(/.*)?"""), "")
            .replace(Regex("""[a-zA-Z0-9.-]+\.supabase\.co(:\d+)?"""), "")
            // Remove generic Exception class names
            .replace(Regex("""[a-zA-Z0-9_.]+(Exception|Error):?"""), "")
            .trim()
    }
}
