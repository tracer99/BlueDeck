package com.bluedeck.data.models

/**
 * UI-only vehicle presets for testing how BlueDeck renders different vehicle/status combinations.
 * These mock profiles never replace the real selected vehicle inside command calls.
 */
enum class MockVehicleProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val fuelLevelPercent: Int? = null
) {
    OFF(
        id = "off",
        displayName = "Live vehicle",
        description = "Use real vehicle data from Hyundai"
    ),
    IONIQ_9_CHARGING(
        id = "ioniq_9_charging",
        displayName = "IONIQ 9 EV · charging",
        description = "Large EV with active AC charging, charge targets, and Digital Key capability"
    ),
    IONIQ_5_UNPLUGGED(
        id = "ioniq_5_unplugged",
        displayName = "IONIQ 5 EV · unplugged",
        description = "EV status with moderate battery, no plug, and climate off"
    ),
    IONIQ_6_LOW_BATTERY(
        id = "ioniq_6_low_battery",
        displayName = "IONIQ 6 EV · low battery",
        description = "Low SOC warning state with open charge port and tire warning"
    ),
    KONA_EV(
        id = "kona_ev",
        displayName = "Kona Electric",
        description = "Compact EV layout with moderate charge and no plug"
    ),
    ELANTRA_GAS(
        id = "elantra_gas",
        displayName = "Elantra",
        description = "Gas sedan layout with normal fuel range"
    ),
    SONATA_HYBRID(
        id = "sonata_hybrid",
        displayName = "Sonata Hybrid",
        description = "Hybrid sedan layout with efficient range and climate off"
    ),
    TUCSON_GAS(
        id = "tucson_gas",
        displayName = "Tucson",
        description = "Gas SUV layout with climate on and doors locked"
    ),
    SANTA_FE_HYBRID(
        id = "santa_fe_hybrid",
        displayName = "Santa Fe Hybrid",
        description = "Hybrid SUV layout with Digital Key and family-hauler status"
    ),
    PALISADE_GAS(
        id = "palisade_gas",
        displayName = "Palisade",
        description = "Large gas SUV layout with engine running and climate on"
    ),
    SANTA_CRUZ_LOW_FUEL(
        id = "santa_cruz_low_fuel",
        displayName = "Santa Cruz · low fuel",
        description = "Truck-style gas layout with a low-fuel warning test state",
        fuelLevelPercent = 8
    );

    val isMock: Boolean get() = this != OFF
    val hasLowFuelWarning: Boolean get() = (fuelLevelPercent ?: 100) <= 10

    fun toVehicle(real: Vehicle?): Vehicle {
        val base = real ?: Vehicle(
            vin = "MOCK-LIVE-VIN",
            vehicleIdentifier = "mock-live-vehicle",
            enrollmentId = "mock-live-enrollment",
            regId = "mock-live-registration",
            modelYear = "2026",
            modelName = "Vehicle",
            brandIndicator = "H"
        )

        return when (this) {
            OFF -> base
            IONIQ_9_CHARGING -> base.copy(
                vin = "MOCK-IONIQ9-CHARGING",
                vehicleIdentifier = "mock-ioniq9",
                enrollmentId = "mock-ioniq9-enrollment",
                regId = "mock-ioniq9-registration",
                nickname = "Mock IONIQ 9",
                modelCode = "IONIQ 9",
                modelName = "IONIQ 9 SEL",
                modelYear = "2026",
                colorName = "Celadon Gray Matte",
                brandIndicator = "H",
                odometer = 1248,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "Y",
                    digitalKeyType = "DK2",
                    wifiHotspotCapable = "Y",
                    v2lOption = "Y",
                    batteryPreconditioningOption = "Y",
                    chargePortDoorOption = "Y",
                    targetSocLevelMax = 100
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            IONIQ_5_UNPLUGGED -> base.copy(
                vin = "MOCK-IONIQ5-UNPLUGGED",
                vehicleIdentifier = "mock-ioniq5",
                enrollmentId = "mock-ioniq5-enrollment",
                regId = "mock-ioniq5-registration",
                nickname = "Mock IONIQ 5",
                modelCode = "IONIQ 5",
                modelName = "IONIQ 5 Limited",
                modelYear = "2025",
                colorName = "Lucid Blue Pearl",
                brandIndicator = "H",
                odometer = 8721,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "N",
                    digitalKeyType = "DK2",
                    v2lOption = "Y",
                    batteryPreconditioningOption = "Y",
                    chargePortDoorOption = "Y",
                    targetSocLevelMax = 100
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            IONIQ_6_LOW_BATTERY -> base.copy(
                vin = "MOCK-IONIQ6-LOW-BATTERY",
                vehicleIdentifier = "mock-ioniq6-low",
                enrollmentId = "mock-ioniq6-low-enrollment",
                regId = "mock-ioniq6-low-registration",
                nickname = "Mock IONIQ 6",
                modelCode = "IONIQ 6",
                modelName = "IONIQ 6 SE",
                modelYear = "2025",
                colorName = "Abyss Black",
                brandIndicator = "H",
                odometer = 23104,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "N",
                    digitalKeyType = "DK2",
                    batteryPreconditioningOption = "Y",
                    chargePortDoorOption = "Y",
                    targetSocLevelMax = 100
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            KONA_EV -> base.copy(
                vin = "MOCK-KONA-EV",
                vehicleIdentifier = "mock-kona-ev",
                enrollmentId = "mock-kona-ev-enrollment",
                regId = "mock-kona-ev-registration",
                nickname = "Mock Kona Electric",
                modelCode = "KONA EV",
                modelName = "Kona Electric Limited",
                modelYear = "2025",
                colorName = "Meta Blue Pearl",
                brandIndicator = "H",
                odometer = 5106,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "N",
                    digitalKeyType = "DK2",
                    batteryPreconditioningOption = "Y",
                    chargePortDoorOption = "Y",
                    targetSocLevelMax = 100
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            ELANTRA_GAS -> base.copy(
                vin = "MOCK-ELANTRA-GAS",
                vehicleIdentifier = "mock-elantra",
                enrollmentId = "mock-elantra-enrollment",
                regId = "mock-elantra-registration",
                nickname = "Mock Elantra",
                modelCode = "ELANTRA",
                modelName = "Elantra Limited",
                modelYear = "2025",
                colorName = "Intense Blue",
                brandIndicator = "H",
                odometer = 6833,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "N",
                    digitalKeyEnrolled = "N",
                    remoteLockConsent = "Y",
                    remoteLockConsentCapable = "Y"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            SONATA_HYBRID -> base.copy(
                vin = "MOCK-SONATA-HYBRID",
                vehicleIdentifier = "mock-sonata-hybrid",
                enrollmentId = "mock-sonata-hybrid-enrollment",
                regId = "mock-sonata-hybrid-registration",
                nickname = "Mock Sonata Hybrid",
                modelCode = "SONATA HYBRID",
                modelName = "Sonata Hybrid Limited",
                modelYear = "2025",
                colorName = "Serenity White",
                brandIndicator = "H",
                odometer = 11492,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "Y",
                    digitalKeyType = "DK2"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            TUCSON_GAS -> base.copy(
                vin = "MOCK-TUCSON-GAS",
                vehicleIdentifier = "mock-tucson",
                enrollmentId = "mock-tucson-enrollment",
                regId = "mock-tucson-registration",
                nickname = "Mock Tucson",
                modelCode = "TUCSON",
                modelName = "Tucson SEL",
                modelYear = "2025",
                colorName = "Ultimate Red",
                brandIndicator = "H",
                odometer = 15433,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "N",
                    digitalKeyType = "DK2",
                    remoteLockConsent = "Y",
                    remoteLockConsentCapable = "Y"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            SANTA_FE_HYBRID -> base.copy(
                vin = "MOCK-SANTA-FE-HYBRID",
                vehicleIdentifier = "mock-santa-fe-hybrid",
                enrollmentId = "mock-santa-fe-hybrid-enrollment",
                regId = "mock-santa-fe-hybrid-registration",
                nickname = "Mock Santa Fe Hybrid",
                modelCode = "SANTA FE HYBRID",
                modelName = "Santa Fe Hybrid Calligraphy",
                modelYear = "2025",
                colorName = "Ultimate Red",
                brandIndicator = "H",
                odometer = 7820,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "Y",
                    digitalKeyType = "DK2"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            PALISADE_GAS -> base.copy(
                vin = "MOCK-PALISADE-GAS",
                vehicleIdentifier = "mock-palisade",
                enrollmentId = "mock-palisade-enrollment",
                regId = "mock-palisade-registration",
                nickname = "Mock Palisade",
                modelCode = "PALISADE",
                modelName = "Palisade Calligraphy",
                modelYear = "2025",
                colorName = "Moonlight Cloud",
                brandIndicator = "H",
                odometer = 12106,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "Y",
                    digitalKeyEnrolled = "N",
                    digitalKeyType = "DK2",
                    remoteLockConsent = "Y",
                    remoteLockConsentCapable = "Y"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
            SANTA_CRUZ_LOW_FUEL -> base.copy(
                vin = "MOCK-SANTA-CRUZ-LOW-FUEL",
                vehicleIdentifier = "mock-santa-cruz",
                enrollmentId = "mock-santa-cruz-enrollment",
                regId = "mock-santa-cruz-registration",
                nickname = "Mock Santa Cruz",
                modelCode = "SANTA CRUZ",
                modelName = "Santa Cruz XRT",
                modelYear = "2025",
                colorName = "Blue Stone",
                brandIndicator = "H",
                odometer = 2205,
                additionalDetails = AdditionalVehicleDetails(
                    digitalKeyCapable = "N",
                    digitalKeyEnrolled = "N",
                    remoteLockConsent = "Y",
                    remoteLockConsentCapable = "Y"
                ),
                seatConfigurations = mockSeatConfigurations()
            )
        }
    }

    fun toStatus(): VehicleStatusData? = when (this) {
        OFF -> null
        IONIQ_9_CHARGING -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            airCtrlOn = true,
            defrost = false,
            battery = BatteryStatus(batteryLevel = 91),
            evStatus = evStatus(
                batteryStatus = 84,
                batteryPlugin = 3,
                batteryCharge = true,
                chargingPowerKw = 7.4,
                rangeMiles = 271.0,
                acTarget = 80,
                dcTarget = 80
            ),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(36, 36, 35, 36),
            seatHeaterVentInfo = SeatHeaterVentInfo(driverSeatHeatState = 1, passengerSeatHeatState = 1),
            totalMileage = 1248
        )
        IONIQ_5_UNPLUGGED -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 88),
            evStatus = evStatus(
                batteryStatus = 62,
                batteryPlugin = 0,
                batteryCharge = false,
                chargingPowerKw = null,
                rangeMiles = 184.0,
                acTarget = 80,
                dcTarget = 90
            ),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(35, 35, 35, 35),
            totalMileage = 8721
        )
        IONIQ_6_LOW_BATTERY -> VehicleStatusData(
            doorLock = false,
            doorLockStatus = "UNLOCKED",
            doorOpenStatus = DoorOpenStatus(frontLeft = 1),
            trunkOpenStatus = true,
            hoodOpenStatus = false,
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 52),
            evStatus = evStatus(
                batteryStatus = 9,
                batteryPlugin = 4,
                batteryCharge = false,
                chargingPowerKw = null,
                rangeMiles = 22.0,
                acTarget = 70,
                dcTarget = 80,
                chargePortDoorOpen = 1
            ),
            tirePressureLamp = TirePressure(frontLeft = 1),
            tirePressureStatus = TirePressureStatus(26, 34, 33, 34),
            windowOpenStatus = WindowOpenStatus(frontLeftLevel = 2),
            washerFluidStatus = true,
            smartKeyBatteryWarning = true,
            totalMileage = 23104
        )
        KONA_EV -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 84),
            evStatus = evStatus(
                batteryStatus = 48,
                batteryPlugin = 0,
                batteryCharge = false,
                chargingPowerKw = null,
                rangeMiles = 141.0,
                acTarget = 80,
                dcTarget = 80
            ),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(35, 35, 34, 34),
            totalMileage = 5106
        )
        ELANTRA_GAS -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            engineStatus = false,
            ignitionStatus = "OFF",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 78),
            dte = Dte(unit = 1, value = 402.0, fuelLevelPercent = 84.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(35, 35, 35, 35),
            totalMileage = 6833
        )
        SONATA_HYBRID -> VehicleStatusData(
            doorLock = false,
            doorLockStatus = "UNLOCKED",
            engineStatus = false,
            ignitionStatus = "OFF",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 81),
            dte = Dte(unit = 1, value = 468.0, fuelLevelPercent = 91.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(35, 35, 35, 35),
            totalMileage = 11492
        )
        TUCSON_GAS -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            engineStatus = false,
            ignitionStatus = "OFF",
            airCtrlOn = true,
            defrost = true,
            battery = BatteryStatus(batteryLevel = 76),
            dte = Dte(unit = 1, value = 286.0, fuelLevelPercent = 58.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(36, 35, 36, 35),
            seatHeaterVentInfo = SeatHeaterVentInfo(driverSeatHeatState = 2, passengerSeatHeatState = 1),
            totalMileage = 15433
        )
        SANTA_FE_HYBRID -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            engineStatus = false,
            ignitionStatus = "OFF",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 79),
            dte = Dte(unit = 1, value = 412.0, fuelLevelPercent = 76.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(34, 34, 34, 34),
            totalMileage = 7820
        )
        PALISADE_GAS -> VehicleStatusData(
            doorLock = true,
            doorLockStatus = "LOCKED",
            engineStatus = true,
            ignitionStatus = "ON",
            airCtrlOn = true,
            defrost = true,
            battery = BatteryStatus(batteryLevel = 83),
            dte = Dte(unit = 1, value = 318.0, fuelLevelPercent = 67.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(36, 35, 36, 35),
            seatHeaterVentInfo = SeatHeaterVentInfo(driverSeatHeatState = 2, passengerSeatHeatState = 1),
            totalMileage = 12106
        )
        SANTA_CRUZ_LOW_FUEL -> VehicleStatusData(
            doorLock = false,
            doorLockStatus = "UNLOCKED",
            engineStatus = false,
            ignitionStatus = "OFF",
            airCtrlOn = false,
            battery = BatteryStatus(batteryLevel = 73),
            dte = Dte(unit = 1, value = 32.0, fuelLevelPercent = 8.0),
            tirePressureLamp = TirePressure(),
            tirePressureStatus = TirePressureStatus(35, 35, 35, 34),
            totalMileage = 2205
        )
    }

    companion object {
        fun fromId(id: String?): MockVehicleProfile = entries.firstOrNull { it.id == id } ?: OFF
    }
}

private fun evStatus(
    batteryStatus: Int,
    batteryPlugin: Int,
    batteryCharge: Boolean,
    chargingPowerKw: Double?,
    rangeMiles: Double,
    acTarget: Int,
    dcTarget: Int,
    chargePortDoorOpen: Int = -1
): EVStatus = EVStatus(
    batteryCharge = batteryCharge,
    batteryStatus = batteryStatus,
    batteryPlugin = batteryPlugin,
    chargingPowerKw = chargingPowerKw,
    drvDistance = listOf(
        DriveDistance(
            rangeByFuel = RangeByFuel(
                totalAvailableRange = RangeValue(value = rangeMiles, unit = 1),
                evModeRange = RangeValue(value = rangeMiles, unit = 1)
            )
        )
    ),
    reservChargeInfos = ReservChargeInfos(
        targets = listOf(
            ChargeTarget(plugType = 1, targetSoc = acTarget),
            ChargeTarget(plugType = 0, targetSoc = dcTarget)
        )
    ),
    chargePortDoorOpen = chargePortDoorOpen,
    batteryPrecondition = batteryStatus <= 20
)

private fun mockSeatConfigurations(): SeatConfigurations = SeatConfigurations(
    seatConfigs = listOf(
        SeatConfig(seatLocationId = "1", heatingCapable = "Y", ventCapable = "Y", supportedLevels = "0,1,2,3"),
        SeatConfig(seatLocationId = "2", heatingCapable = "Y", ventCapable = "Y", supportedLevels = "0,1,2,3"),
        SeatConfig(seatLocationId = "3", heatingCapable = "Y", ventCapable = "N", supportedLevels = "0,1,2,3"),
        SeatConfig(seatLocationId = "4", heatingCapable = "Y", ventCapable = "N", supportedLevels = "0,1,2,3")
    )
)
