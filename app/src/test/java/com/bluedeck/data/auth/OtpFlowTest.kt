package com.bluedeck.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanadaOtpFlowTest {

    @Test
    fun detectsOtpRequiredFrom7110() {
        val json = parseJson(
            """
            {
              "error": { "errorCode": "7110" },
              "responseHeader": { "responseCode": 1, "responseDesc": "Failure" }
            }
            """
        )
        assertTrue(CanadaMfaResponses.isOtpRequired(json))
    }

    @Test
    fun parsesVerificationMethods() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": {
                "userInfoUuid": "ff36138e-4aa8-4030-ba5d-25090485fece",
                "otpKey": "",
                "emailList": ["user@example.com"],
                "userPhone": "514-555-1234"
              }
            }
            """
        )
        val methods = CanadaMfaResponses.parseVerificationMethods(json)
        assertNotNull(methods)
        assertEquals("ff36138e-4aa8-4030-ba5d-25090485fece", methods?.userInfoUuid)
        assertEquals("user@example.com", methods?.email)
        assertEquals("514-555-1234", methods?.phone)
    }

    @Test
    fun parsesVerificationMethodsFromUserAccountWhenEmailListMissing() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": {
                "userInfoUuid": "ff36138e-4aa8-4030-ba5d-25090485fece",
                "userPhone": "(***) ***-1234",
                "userAccount": "USER@EXAMPLE.COM"
              }
            }
            """
        )
        val methods = CanadaMfaResponses.parseVerificationMethods(json)
        assertNotNull(methods)
        assertEquals("USER@EXAMPLE.COM", methods?.email)
        assertEquals("USER@EXAMPLE.COM", methods?.userAccount)
        assertEquals("(***) ***-1234", methods?.phone)
    }

    @Test
    fun passesPartialMaskedPhoneForSmsSend() {
        assertFalse(CanadaMfaResponses.smsPhoneForSend("XXXX").isNotBlank())
        assertEquals("(***) ***-1234", CanadaMfaResponses.smsPhoneForSend("(***) ***-1234"))
        assertTrue(CanadaMfaResponses.hasSmsDestination("(***) ***-1234"))
        assertEquals("514-555-1234", CanadaMfaResponses.smsPhoneForSend("514-555-1234"))
    }

    @Test
    fun parsesStringResponseCodeAsSuccess() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": "0", "responseDesc": "Success" },
              "result": { "otpKey": "abc" }
            }
            """
        )
        assertTrue(CanadaMfaResponses.isSuccess(json))
    }

    @Test
    fun treatsMissingVerifiedOtpAsSuccessWhenValidationKeyPresent() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": { "otpValidationKey": "validation-key" }
            }
            """
        )
        val validated = CanadaMfaResponses.parseValidatedOtp(json)
        assertNotNull(validated)
        assertTrue(validated?.verified == true)
    }

    @Test
    fun parsesSendOtpKey() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": { "otpKey": "NzY0NmFhNzEtNTc3My00ZGM3LTg4ODItM2Y3MTJjNjU" }
            }
            """
        )
        assertEquals("NzY0NmFhNzEtNTc3My00ZGM3LTg4ODItM2Y3MTJjNjU", CanadaMfaResponses.parseSendOtpKey(json))
    }

    @Test
    fun parsesValidatedOtp() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": {
                "otpValidationKey": "validation-key",
                "verifiedOtp": true
              }
            }
            """
        )
        val validated = CanadaMfaResponses.parseValidatedOtp(json)
        assertNotNull(validated)
        assertEquals("validation-key", validated?.otpValidationKey)
        assertTrue(validated?.verified == true)
    }

    @Test
    fun parsesMfaToken() {
        val json = parseJson(
            """
            {
              "responseHeader": { "responseCode": 0, "responseDesc": "Success" },
              "result": {
                "verifiedTnC": true,
                "token": {
                  "accessToken": "access-123",
                  "refreshToken": "refresh-456",
                  "expireIn": 86400
                }
              }
            }
            """
        )
        val token = CanadaMfaResponses.parseMfaToken(json)
        assertNotNull(token)
        assertEquals("access-123", token?.accessToken)
        assertEquals("refresh-456", token?.refreshToken)
        assertEquals(86400, token?.expireIn)
    }

    private fun parseJson(raw: String): JsonObject =
        JsonParser.parseString(raw.trimIndent()).asJsonObject
}

class KiaUsOtpFlowTest {

    @Test
    fun detectsOtpKeyInPayload() {
        val json = parseJson(
            """
            {
              "payload": {
                "otpKey": "abc123",
                "hasEmail": true,
                "hasPhone": false,
                "email": "user@example.com"
              }
            }
            """
        )
        assertEquals("abc123", KiaUsOtpResponses.hasOtpKey(json))
    }

    @Test
    fun selectsEmailWhenAvailable() {
        val options = KiaUsOtpResponses.ContactOptions(
            hasEmail = true,
            hasPhone = true,
            email = "user@example.com",
            phone = "555-0100",
            refreshTokenExpired = false
        )
        assertEquals(OtpDeliveryMethod.EMAIL, KiaUsOtpResponses.preferredDeliveryMethod(options))
        assertEquals(setOf(OtpDeliveryMethod.EMAIL, OtpDeliveryMethod.SMS), KiaUsOtpResponses.availableMethods(options))
        assertEquals("email user@example.com", KiaUsOtpResponses.destinationLabel(OtpDeliveryMethod.EMAIL, options))
    }

    @Test
    fun fallsBackToSmsWhenNoEmail() {
        val options = KiaUsOtpResponses.ContactOptions(
            hasEmail = false,
            hasPhone = true,
            email = null,
            phone = "555-0100",
            refreshTokenExpired = true
        )
        assertEquals(OtpDeliveryMethod.SMS, KiaUsOtpResponses.preferredDeliveryMethod(options))
        assertFalse(KiaUsOtpResponses.availableMethods(options).contains(OtpDeliveryMethod.EMAIL))
    }

    @Test
    fun returnsNullWhenNoOtpKey() {
        val json = parseJson("""{ "payload": { "hasEmail": true } }""")
        assertNull(KiaUsOtpResponses.hasOtpKey(json))
    }

    private fun parseJson(raw: String): JsonObject =
        JsonParser.parseString(raw.trimIndent()).asJsonObject
}

class OtpChallengeTest {

    @Test
    fun canadaChallengeSupportsTrustDevice() {
        val challenge = PendingOtpChallenge.Canada(
            username = "user@example.com",
            password = "secret",
            servicePin = "1234",
            destinationLabel = "email user@example.com",
            availableMethods = setOf(OtpDeliveryMethod.EMAIL),
            selectedMethod = OtpDeliveryMethod.EMAIL,
            userInfoUuid = "uuid",
            otpKey = "key",
            email = "user@example.com",
            phone = null
        )
        assertTrue(challenge.supportsTrustDevice)
    }

    @Test
    fun kiaUsChallengeDoesNotSupportTrustDevice() {
        val challenge = PendingOtpChallenge.KiaUs(
            username = "user@example.com",
            password = "secret",
            servicePin = "1234",
            destinationLabel = "email user@example.com",
            availableMethods = setOf(OtpDeliveryMethod.EMAIL),
            selectedMethod = OtpDeliveryMethod.EMAIL,
            otpKey = "key",
            xid = "xid",
            refreshTokenExpired = false,
            email = "user@example.com",
            phone = null
        )
        assertFalse(challenge.supportsTrustDevice)
    }
}
