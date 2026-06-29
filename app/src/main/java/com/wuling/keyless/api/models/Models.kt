package com.wuling.keyless.api.models

data class VehicleStatusResult(
    val success: Boolean = false,
    val vin: String = "",
    val carName: String = "",
    val batterySoc: String = "",
    val mileage: String = "",
    val doorLockStatus: String = "",
    val acStatus: String = "",
    val error: String? = null
)

data class CommandResult(
    val success: Boolean = false,
    val message: String = "",
    val error: String? = null
)
