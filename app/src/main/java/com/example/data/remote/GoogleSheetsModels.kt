package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "token_type") val tokenType: String
)

@JsonClass(generateAdapter = true)
data class ValueRange(
    @Json(name = "range") val range: String? = null,
    @Json(name = "majorDimension") val majorDimension: String? = "ROWS",
    @Json(name = "values") val values: List<List<String>>
)

@JsonClass(generateAdapter = true)
data class AppendResponse(
    @Json(name = "spreadsheetId") val spreadsheetId: String? = null,
    @Json(name = "tableRange") val tableRange: String? = null
)
