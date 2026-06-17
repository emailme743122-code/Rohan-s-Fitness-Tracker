package com.example.data.remote

import android.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

interface GoogleSheetsService {

    @FormUrlEncoded
    @POST("https://oauth2.googleapis.com/token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer",
        @Field("assertion") assertion: String
    ): TokenResponse

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getSheetValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Header("Authorization") authorization: String
    ): ValueRange

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendSheetValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Header("Authorization") authorization: String,
        @Body valueRange: ValueRange
    ): AppendResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateSheetValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Header("Authorization") authorization: String,
        @Body valueRange: ValueRange
    ): ValueRange
}

object ServiceAccountAuth {

    fun generateJwt(serviceAccountJson: String, scope: String): String? {
        return try {
            val json = JSONObject(serviceAccountJson)
            val clientEmail = json.getString("client_email")
            val privateKeyString = json.getString("private_key")

            val header = JSONObject().apply {
                put("alg", "RS256")
                put("typ", "JWT")
            }.toString()

            val nowSeconds = System.currentTimeMillis() / 1000
            val expSeconds = nowSeconds + 3600

            val payload = JSONObject().apply {
                put("iss", clientEmail)
                put("scope", scope)
                put("aud", "https://oauth2.googleapis.com/token")
                put("exp", expSeconds)
                put("iat", nowSeconds)
            }.toString()

            val headerBase64 = Base64.encodeToString(header.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
            val payloadBase64 = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

            val stringToSign = "$headerBase64.$payloadBase64"

            // Clean PEM private key
            val privateKeyPem = privateKeyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
                .replace("\\n".toRegex(), "")
                .replace("\n", "")
                .trim()

            val keyBytes = Base64.decode(privateKeyPem, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(keySpec)

            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(stringToSign.toByteArray())
            }.sign()

            val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

            "$stringToSign.$signatureBase64"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class GoogleSheetsClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val api: GoogleSheetsService = Retrofit.Builder()
        .baseUrl("https://sheets.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GoogleSheetsService::class.java)

    // Cached token
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiryTimeMs: Long = 0

    private suspend fun getValidAccessToken(serviceAccountJson: String): String {
        return tokenMutex.withLock {
            val now = System.currentTimeMillis()
            if (cachedToken != null && now < tokenExpiryTimeMs) {
                return@withLock cachedToken!!
            }

            val assertionJwt = ServiceAccountAuth.generateJwt(
                serviceAccountJson,
                "https://www.googleapis.com/auth/spreadsheets"
            ) ?: throw IllegalArgumentException("Failed to generate JWT from service account.")

            val tokenResp = api.getAccessToken(assertion = assertionJwt)
            cachedToken = tokenResp.accessToken
            tokenExpiryTimeMs = now + (tokenResp.expiresIn * 1000) - 60000 // Buffer of 1 minute

            tokenResp.accessToken
        }
    }

    suspend fun getSheetValues(serviceAccountJson: String, spreadsheetId: String, range: String): List<List<String>> {
        val token = getValidAccessToken(serviceAccountJson)
        val response = api.getSheetValues(spreadsheetId, range, "Bearer $token")
        return response.values
    }

    suspend fun appendSheetValues(serviceAccountJson: String, spreadsheetId: String, range: String, values: List<List<String>>) {
        val token = getValidAccessToken(serviceAccountJson)
        val body = ValueRange(values = values)
        api.appendSheetValues(spreadsheetId, range, "USER_ENTERED", "Bearer $token", body)
    }

    suspend fun updateSheetValues(serviceAccountJson: String, spreadsheetId: String, range: String, values: List<List<String>>) {
        val token = getValidAccessToken(serviceAccountJson)
        val body = ValueRange(values = values)
        api.updateSheetValues(spreadsheetId, range, "USER_ENTERED", "Bearer $token", body)
    }
}
