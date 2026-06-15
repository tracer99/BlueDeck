package com.bluedeck.data.auth

import com.google.gson.JsonObject

object CanadaMfaResponses {
    const val MFA_API_CODE = "0107"
    const val ERROR_OTP_REQUIRED = "7110"
    const val ERROR_OTP_FAILED = "7549"

    fun isOtpRequired(json: JsonObject?): Boolean =
        json?.objectOrNull("error")?.stringOrNull("errorCode") == ERROR_OTP_REQUIRED

    fun isSuccess(json: JsonObject?): Boolean = responseCode(json) == 0

    fun responseCode(json: JsonObject?): Int? {
        val header = json?.objectOrNull("responseHeader") ?: return null
        header.intOrNull("responseCode")?.let { return it }
        return header.stringOrNull("responseCode")?.toIntOrNull()
    }

    data class VerificationMethods(
        val userInfoUuid: String,
        val email: String?,
        val phone: String?,
        val userAccount: String?
    )

    fun parseVerificationMethods(json: JsonObject?): VerificationMethods? {
        val result = json?.objectOrNull("result") ?: return null
        val userInfoUuid = result.stringOrNull("userInfoUuid") ?: return null
        val emailList = result.arrayOrNull("emailList")
            ?.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val apiUserAccount = result.stringOrNull("userAccount")?.takeIf { it.isNotBlank() }
        val email = emailList.firstOrNull() ?: apiUserAccount
        val phone = result.stringOrNull("userPhone")?.takeIf { it.isNotBlank() }
        return VerificationMethods(
            userInfoUuid = userInfoUuid,
            email = email,
            phone = phone,
            userAccount = apiUserAccount ?: email
        )
    }

    /** Pass masked/partial numbers through when the API returned digits (e.g. ***-1234). */
    fun smsPhoneForSend(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        if (phone.count { it.isDigit() } == 0) return ""
        return phone.trim()
    }

    fun hasSmsDestination(phone: String?): Boolean = !smsPhoneForSend(phone).isBlank()

    fun parseSendOtpKey(json: JsonObject?): String? =
        json?.objectOrNull("result")?.stringOrNull("otpKey")?.takeIf { it.isNotBlank() }

    data class ValidatedOtp(
        val otpValidationKey: String,
        val verified: Boolean
    )

    fun parseValidatedOtp(json: JsonObject?): ValidatedOtp? {
        val result = json?.objectOrNull("result") ?: return null
        val key = result.stringOrNull("otpValidationKey") ?: return null
        val verifiedField = result.get("verifiedOtp")
        val verified = when {
            verifiedField == null || verifiedField.isJsonNull -> true
            verifiedField.isJsonPrimitive && verifiedField.asJsonPrimitive.isBoolean ->
                verifiedField.asBoolean
            verifiedField.isJsonPrimitive && verifiedField.asJsonPrimitive.isNumber ->
                verifiedField.asInt != 0
            else ->
                result.stringOrNull("verifiedOtp")?.let { value ->
                    value.equals("true", ignoreCase = true) ||
                        value.equals("y", ignoreCase = true) ||
                        value == "1"
                } == true
        }
        return ValidatedOtp(otpValidationKey = key, verified = verified)
    }

    data class MfaToken(
        val accessToken: String,
        val refreshToken: String,
        val expireIn: Int
    )

    fun parseMfaToken(json: JsonObject?): MfaToken? {
        val token = json?.objectOrNull("result")?.objectOrNull("token") ?: return null
        val accessToken = token.stringOrNull("accessToken") ?: return null
        val refreshToken = token.stringOrNull("refreshToken").orEmpty()
        val expireIn = token.intOrNull("expireIn") ?: 86400
        return MfaToken(accessToken = accessToken, refreshToken = refreshToken, expireIn = expireIn)
    }
}

object KiaUsOtpResponses {
    fun hasOtpKey(json: JsonObject?): String? =
        json?.objectOrNull("payload")?.stringOrNull("otpKey")?.takeIf { it.isNotBlank() }

    data class ContactOptions(
        val hasEmail: Boolean,
        val hasPhone: Boolean,
        val email: String?,
        val phone: String?,
        val refreshTokenExpired: Boolean
    )

    fun parseContactOptions(json: JsonObject?): ContactOptions? {
        val payload = json?.objectOrNull("payload") ?: return null
        val hasEmail = payload.stringOrNull("hasEmail")?.equals("true", ignoreCase = true) == true
            || payload.intOrNull("hasEmail") == 1
        val hasPhone = payload.stringOrNull("hasPhone")?.equals("true", ignoreCase = true) == true
            || payload.intOrNull("hasPhone") == 1
        val refreshTokenExpired = payload.stringOrNull("rmTokenExpired")?.equals("true", ignoreCase = true) == true
            || payload.intOrNull("rmTokenExpired") == 1
        return ContactOptions(
            hasEmail = hasEmail,
            hasPhone = hasPhone,
            email = payload.stringOrNull("email"),
            phone = payload.stringOrNull("phone"),
            refreshTokenExpired = refreshTokenExpired
        )
    }

    fun preferredDeliveryMethod(options: ContactOptions): OtpDeliveryMethod =
        if (options.hasEmail) OtpDeliveryMethod.EMAIL else OtpDeliveryMethod.SMS

    fun availableMethods(options: ContactOptions): Set<OtpDeliveryMethod> = buildSet {
        if (options.hasEmail) add(OtpDeliveryMethod.EMAIL)
        if (options.hasPhone) add(OtpDeliveryMethod.SMS)
        if (isEmpty()) add(OtpDeliveryMethod.EMAIL)
    }

    fun destinationLabel(method: OtpDeliveryMethod, options: ContactOptions): String = when (method) {
        OtpDeliveryMethod.EMAIL -> options.email?.let { "email $it" } ?: "email"
        OtpDeliveryMethod.SMS -> when {
            CanadaMfaResponses.hasSmsDestination(options.phone) -> "phone ${options.phone}"
            else -> "your phone"
        }
    }

    fun isUsableSmsPhone(phone: String?): Boolean = CanadaMfaResponses.hasSmsDestination(phone)

    fun notifyType(method: OtpDeliveryMethod): String = when (method) {
        OtpDeliveryMethod.EMAIL -> "EMAIL"
        OtpDeliveryMethod.SMS -> "SMS"
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.intOrNull(key: String): Int? =
    get(key)?.takeIf { !it.isJsonNull }?.asInt

private fun JsonObject.objectOrNull(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arrayOrNull(key: String) =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray
