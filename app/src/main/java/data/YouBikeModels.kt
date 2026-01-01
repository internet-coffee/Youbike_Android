package com.android.youbike.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class StationInfo(
    @SerialName("station_no") val stationNo: String,
    @SerialName("name_tw") val name: String,
    @SerialName("district_tw") val district: String,
    @SerialName("address_tw") val address: String,
    @SerialName("lat") val latitude: Double,
    @SerialName("lng") val longitude: Double
)

@Serializable
data class StationRequest(
    @SerialName("station_no") val stationNumbers: List<String>
)

@Serializable
data class ParkingInfoResponse(
    @SerialName("retVal") val retVal: RetValData
)

@Serializable
data class RetValData(
    @SerialName("data") val data: List<VehicleInfo>
)

@Immutable
@Serializable
data class VehicleInfo(
    @SerialName("station_no") val stationNo: String,
    @SerialName("empty_spaces") val emptySpaces: Int,
    @SerialName("available_spaces_detail") val vehicleDetails: VehicleDetail
)

@Immutable
@Serializable
data class VehicleDetail(
    @SerialName("yb2") val youbike2: Int,
    @SerialName("eyb") val youbike2E: Int
)