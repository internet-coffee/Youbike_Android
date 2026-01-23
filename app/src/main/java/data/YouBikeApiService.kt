package data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

val commonJson = Json { ignoreUnknownKeys = true }

private const val BASE_URL = "https://apis.youbike.com.tw/"

private val retrofit = Retrofit.Builder()
    .addConverterFactory(commonJson.asConverterFactory("application/json".toMediaType()))
    .baseUrl(BASE_URL)
    .build()

interface YouBikeApiService {
    @GET("json/station-min-yb2.json")
    suspend fun getAllStations(): List<StationInfo>

    @Headers(
        "accept: */*",
        "content-type: application/json"
    )
    @POST("tw2/parkingInfo")
    suspend fun getParkingInfo(@Body stationRequest: StationRequest): ParkingInfoResponse
}

object YouBikeApi {
    val retrofitService: YouBikeApiService by lazy {
        retrofit.create(YouBikeApiService::class.java)
    }
}