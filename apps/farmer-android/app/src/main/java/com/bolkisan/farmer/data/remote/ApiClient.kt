package com.bolkisan.farmer.data.remote

import android.content.Context
import android.os.SystemClock
import com.bolkisan.farmer.BuildConfig
import com.bolkisan.farmer.data.remote.models.BackendHealthDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    CONNECTED,
    SERVER_UNREACHABLE,
    INVALID_URL,
    TIMEOUT,
    BACKEND_ERROR
}

data class BackendConnectionResult(
    val status: ConnectionStatus,
    val latencyMs: Long,
    val targetUrl: String,
    val httpCode: Int? = null,
    val healthInfo: BackendHealthDto? = null,
    val errorMessage: String? = null
)

data class UrlValidationResult(
    val isValid: Boolean,
    val sanitizedUrl: String,
    val errorMessage: String? = null
)

object ApiClient {

    private const val PREFS_NAME = "bolkisan_network_prefs"
    private const val KEY_DEBUG_URL = "debug_api_base_url"

    // Default endpoints
    const val EMULATOR_URL = "http://10.0.2.2:8000/api/v1/"
    val DEFAULT_LAN_URL = "http://${BuildConfig.DEV_BACKEND_IP}:8000/api/v1/"
    const val PROD_URL = "https://api.bolkisan.gov.in/api/v1/"

    @Volatile
    private var currentBaseUrl: String? = null

    @Volatile
    private var currentRetrofit: Retrofit? = null

    @Volatile
    private var currentApiService: BolKisanApiService? = null

    private val lock = Any()

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
        redactHeader("Cookie")
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val orig = chain.request()
            val origUrlStr = orig.url.toString()
            val newUrlStr = if (origUrlStr.contains("/api/v1/api/v1/")) {
                origUrlStr.replace("/api/v1/api/v1/", "/api/v1/")
            } else {
                origUrlStr
            }
            val builder = orig.newBuilder().url(newUrlStr)
            if (orig.header("Authorization") == null) {
                val token = com.bolkisan.farmer.data.repository.BolKisanRepository.activeInstance?.currentToken
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            chain.proceed(builder.build())
        }
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun getDevBackendIp(): String = BuildConfig.DEV_BACKEND_IP
    fun getDefaultBaseUrl(): String = BuildConfig.CONFIGURED_API_BASE_URL.ifBlank { EMULATOR_URL }
    fun setBaseUrl(newUrl: String, context: Context): UrlValidationResult = setDebugBaseUrl(context, newUrl)

    /**
     * Resolves the active Base URL with environment safety:
     * - Production release builds ALWAYS return PROD_URL (HTTPS), ignoring any local developer overrides.
     * - Debug builds check SharedPreferences first, then fall back to BuildConfig configured URL.
     */
    fun getBaseUrl(context: Context? = null): String {
        if (BuildConfig.IS_PRODUCTION) {
            return PROD_URL
        }

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_DEBUG_URL, null)
            if (!custom.isNullOrBlank()) {
                val validation = validateBaseUrl(custom)
                if (validation.isValid) {
                    return validation.sanitizedUrl
                }
            }
        }

        return BuildConfig.CONFIGURED_API_BASE_URL.ifBlank { EMULATOR_URL }
    }

    /**
     * Validates candidate Base URL before configuring Retrofit:
     * - Requires http or https scheme.
     * - Disallows cleartext HTTP when in production mode.
     * - Ensures valid host name or IP address.
     * - Ensures valid port (1..65535) if specified.
     * - Ensures trailing slash and auto-appends /api/v1/ if bare host:port was provided.
     */
    fun validateBaseUrl(candidate: String, isProduction: Boolean = BuildConfig.IS_PRODUCTION): UrlValidationResult {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) {
            return UrlValidationResult(false, "", "URL cannot be blank.")
        }

        val parsed = trimmed.toHttpUrlOrNull()
            ?: return UrlValidationResult(false, "", "Malformed URL syntax: '$trimmed'")

        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlValidationResult(false, "", "Unsupported protocol '$scheme'. Only http:// or https:// allowed.")
        }

        if (isProduction && scheme != "https") {
            return UrlValidationResult(false, "", "Production builds strictly require secure HTTPS.")
        }

        if (parsed.host.isBlank()) {
            return UrlValidationResult(false, "", "Host address cannot be empty.")
        }

        var normalized = trimmed
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }

        // If user entered only http://192.168.0.150:8000/, auto-append api/v1/
        if (!normalized.endsWith("/api/v1/")) {
            normalized = normalized.removeSuffix("/") + "/api/v1/"
        }

        return UrlValidationResult(true, normalized, null)
    }

    /**
     * Saves a developer-configured debug API base URL.
     * Recreates the Retrofit instance immediately so subsequent calls use the new endpoint.
     */
    fun setDebugBaseUrl(context: Context, newUrl: String): UrlValidationResult {
        if (BuildConfig.IS_PRODUCTION) {
            return UrlValidationResult(false, PROD_URL, "Cannot modify Base URL in production release build.")
        }

        val validation = validateBaseUrl(newUrl)
        if (!validation.isValid) {
            return validation
        }

        synchronized(lock) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEBUG_URL, validation.sanitizedUrl)
                .apply()

            currentBaseUrl = validation.sanitizedUrl
            currentRetrofit = null
            currentApiService = null
        }

        return validation
    }

    /**
     * Resets the debug base URL back to default build configuration (Emulator 10.0.2.2 or build property).
     */
    fun resetToDefaultUrl(context: Context) {
        synchronized(lock) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DEBUG_URL)
                .apply()

            currentBaseUrl = null
            currentRetrofit = null
            currentApiService = null
        }
    }

    /**
     * Returns the active BolKisanApiService instance, re-initializing Retrofit if the Base URL was changed.
     */
    fun getApiService(context: Context? = null): BolKisanApiService {
        val targetUrl = getBaseUrl(context)
        if (currentApiService != null && currentBaseUrl == targetUrl) {
            return currentApiService!!
        }

        return synchronized(lock) {
            if (currentApiService != null && currentBaseUrl == targetUrl) {
                currentApiService!!
            } else {
                val retrofit = Retrofit.Builder()
                    .baseUrl(targetUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                currentBaseUrl = targetUrl
                currentRetrofit = retrofit
                currentApiService = retrofit.create(BolKisanApiService::class.java)
                currentApiService!!
            }
        }
    }

    // Default accessor for existing callers without explicit Context
    val apiService: BolKisanApiService
        get() = getApiService(null)

    /**
     * Actively tests backend connectivity by querying GET /health.
     * Measures response latency with SystemClock.elapsedRealtime().
     * Accurately categorizes errors (TIMEOUT, SERVER_UNREACHABLE, INVALID_URL, BACKEND_ERROR).
     */
    suspend fun checkConnection(
        candidateUrl: String? = null,
        context: Context? = null
    ): BackendConnectionResult = withContext(Dispatchers.IO) {
        val base = candidateUrl ?: getBaseUrl(context)
        val validation = validateBaseUrl(base)
        if (!validation.isValid) {
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.INVALID_URL,
                latencyMs = 0L,
                targetUrl = base,
                errorMessage = validation.errorMessage
            )
        }

        val targetBase = validation.sanitizedUrl
        // Target /api/v1/health or /health
        val healthUrl = targetBase.removeSuffix("/") + "/health"

        val fastTestClient = okHttpClient.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(healthUrl)
            .get()
            .build()

        val startMonotonic = SystemClock.elapsedRealtime()
        try {
            fastTestClient.newCall(request).execute().use { response ->
                val elapsed = SystemClock.elapsedRealtime() - startMonotonic
                val code = response.code
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val healthDto = try {
                        Gson().fromJson(bodyStr, BackendHealthDto::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    return@withContext BackendConnectionResult(
                        status = ConnectionStatus.CONNECTED,
                        latencyMs = elapsed,
                        targetUrl = healthUrl,
                        httpCode = code,
                        healthInfo = healthDto
                    )
                } else {
                    return@withContext BackendConnectionResult(
                        status = ConnectionStatus.BACKEND_ERROR,
                        latencyMs = elapsed,
                        targetUrl = healthUrl,
                        httpCode = code,
                        errorMessage = "HTTP $code: ${response.message} - $bodyStr"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.TIMEOUT,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "Connection timed out after ${elapsed}ms: ${e.message}"
            )
        } catch (e: ConnectException) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.SERVER_UNREACHABLE,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "Connection refused or unreachable: ${e.message}. Ensure Uvicorn is running on 0.0.0.0:8000 and firewall allows port 8000."
            )
        } catch (e: NoRouteToHostException) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.SERVER_UNREACHABLE,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "No route to host: ${e.message}. Ensure phone and laptop are on the same Wi-Fi network."
            )
        } catch (e: UnknownHostException) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.SERVER_UNREACHABLE,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "Unknown host: ${e.message}. Verify the IP address."
            )
        } catch (e: IOException) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.SERVER_UNREACHABLE,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "I/O Network failure: ${e.message}"
            )
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startMonotonic
            return@withContext BackendConnectionResult(
                status = ConnectionStatus.INVALID_URL,
                latencyMs = elapsed,
                targetUrl = healthUrl,
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }
}
