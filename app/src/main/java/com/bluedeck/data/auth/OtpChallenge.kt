package com.bluedeck.data.auth

const val OTP_REQUIRED_CODE = 460

enum class OtpDeliveryMethod {
    EMAIL,
    SMS;

    fun label(): String = when (this) {
        EMAIL -> "Email"
        SMS -> "Text message"
    }
}

sealed class PendingOtpChallenge {
    abstract val username: String
    abstract val password: String
    abstract val servicePin: String
    abstract val destinationLabel: String
    abstract val availableMethods: Set<OtpDeliveryMethod>
    abstract val selectedMethod: OtpDeliveryMethod
    abstract val supportsTrustDevice: Boolean

    data class KiaUs(
        override val username: String,
        override val password: String,
        override val servicePin: String,
        override val destinationLabel: String,
        override val availableMethods: Set<OtpDeliveryMethod>,
        override val selectedMethod: OtpDeliveryMethod,
        val otpKey: String,
        val xid: String,
        val refreshTokenExpired: Boolean,
        val email: String?,
        val phone: String?
    ) : PendingOtpChallenge() {
        override val supportsTrustDevice: Boolean = false
    }

    data class Canada(
        override val username: String,
        override val password: String,
        override val servicePin: String,
        override val destinationLabel: String,
        override val availableMethods: Set<OtpDeliveryMethod>,
        override val selectedMethod: OtpDeliveryMethod,
        val userInfoUuid: String,
        val otpKey: String?,
        val email: String,
        val phone: String?,
        val rememberDevice: Boolean = true
    ) : PendingOtpChallenge() {
        override val supportsTrustDevice: Boolean = true
    }
}

data class OtpChallengeUi(
    val destinationLabel: String,
    val availableMethods: Set<OtpDeliveryMethod>,
    val selectedMethod: OtpDeliveryMethod,
    val supportsTrustDevice: Boolean,
    val rememberDeviceDefault: Boolean = true,
    val message: String? = null
)

class OtpRequiredException(
    message: String = "Verification code required. Enter the code sent to your email or phone."
) : IllegalStateException(message)
