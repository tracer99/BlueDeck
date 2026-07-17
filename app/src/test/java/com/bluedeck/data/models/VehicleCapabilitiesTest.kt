package com.bluedeck.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCapabilitiesTest {

    @Test
    fun rearSeatExplicitNoHeat_hidesRearInUi() {
        val vehicle = vehicleWithSeats(
            SeatConfig("3", heatingCapable = "N", ventCapable = "N"),
            SeatConfig("4", heatingCapable = "N", ventCapable = "N"),
            SeatConfig("1", heatingCapable = "Y", ventCapable = "Y"),
            SeatConfig("2", heatingCapable = "Y", ventCapable = "Y")
        )
        val caps = vehicle.resolveCapabilities()
        assertFalse(caps.rearLeft.showHeat)
        assertFalse(caps.rearRight.showHeat)
        assertTrue(caps.driver.showHeat)
    }

    @Test
    fun partialSeatList_hidesMissingRearSeats() {
        val vehicle = vehicleWithSeats(
            SeatConfig("1", heatingCapable = "Y", ventCapable = "Y"),
            SeatConfig("2", heatingCapable = "Y", ventCapable = "Y")
        )
        val caps = vehicle.resolveCapabilities()
        assertFalse(caps.rearLeft.showHeat)
        assertFalse(caps.rearRight.showHeat)
        assertTrue(caps.driver.showHeat)
    }

    @Test
    fun missingSeatConfig_showsHeatButNotVent() {
        val vehicle = Vehicle()
        val caps = vehicle.resolveCapabilities()
        assertTrue(caps.rearLeft.showHeat)
        assertTrue(caps.rearRight.showHeat)
        assertTrue(caps.driver.showHeat)
        // Without API seat configs, do not assume cooled/ventilated seats (e.g. IONIQ 9).
        assertFalse(caps.driver.showVent)
        assertFalse(caps.driver.ventCapableForSelector)
        assertFalse(caps.passenger.ventCapableForSelector)
    }

    @Test
    fun missingSeatConfig_clampsCoolPresetOff() {
        val vehicle = Vehicle()
        val clamped = vehicle.clampClimateSeatSettings(
            ClimateSeatSettings(driverSeat = 4, passengerSeat = 4, rearLeftSeat = 2, rearRightSeat = 2)
        )
        assertEquals(2, clamped.driverSeat)
        assertEquals(2, clamped.passengerSeat)
    }

    @Test
    fun supportedLevelsInferHeatAndVent() {
        val vehicle = vehicleWithSeats(
            SeatConfig("3", heatingCapable = "", ventCapable = "", supportedLevels = "0,1,2,6")
        )
        val caps = vehicle.resolveCapabilities()
        assertTrue(caps.rearLeft.showHeat)
        assertFalse(caps.rearLeft.showVent)
    }

    @Test
    fun digitalKeyNotCapable_hidesTile() {
        val vehicle = Vehicle(
            additionalDetails = AdditionalVehicleDetails(digitalKeyCapable = "N")
        )
        assertFalse(vehicle.resolveCapabilities().showDigitalKey)
    }

    @Test
    fun digitalKeyUnknown_showsTile() {
        val vehicle = Vehicle(additionalDetails = AdditionalVehicleDetails(digitalKeyCapable = ""))
        assertTrue(vehicle.resolveCapabilities().showDigitalKey)
    }

    @Test
    fun clampSetsUnsupportedRearSeatsOff() {
        val vehicle = vehicleWithSeats(
            SeatConfig("1", heatingCapable = "Y"),
            SeatConfig("2", heatingCapable = "Y"),
            SeatConfig("3", heatingCapable = "N"),
            SeatConfig("4", heatingCapable = "N")
        )
        val clamped = vehicle.clampClimateSeatSettings(
            ClimateSeatSettings(rearLeftSeat = 7, rearRightSeat = 8, driverSeat = 7)
        )
        assertEquals(2, clamped.rearLeftSeat)
        assertEquals(2, clamped.rearRightSeat)
        assertEquals(7, clamped.driverSeat)
    }

    @Test
    fun euRegion_hidesLocationTile() {
        val vehicle = Vehicle()
        assertFalse(vehicle.resolveCapabilities("EU_HYUNDAI").showLocation)
        assertTrue(vehicle.resolveCapabilities("US_HYUNDAI").showLocation)
    }

    private fun vehicleWithSeats(vararg seats: SeatConfig): Vehicle =
        Vehicle(seatConfigurations = SeatConfigurations(seats.toList()))
}
