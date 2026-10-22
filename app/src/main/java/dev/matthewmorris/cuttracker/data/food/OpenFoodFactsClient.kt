package dev.matthewmorris.cuttracker.data.food

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface OnlineFoodSource {
    /** Null when Open Food Facts doesn't know the barcode, or has no calories for it. */
    suspend fun byBarcode(barcode: String): FoodProduct?

    /** Serving and pack sizes and categories, which search results don't carry. Null if unknown. */
    suspend fun details(barcode: String): ProductDetails?

    suspend fun search(query: String): List<FoodProduct>
}

private interface ProductApi {
    @GET("api/v2/product/{barcode}")
    suspend fun product(@Path("barcode") barcode: String, @Query("fields") fields: String = OffMapping.FIELDS): OffProductResponse
}

private interface SearchApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page_size") pageSize: Int = 20,
        @Query("langs") langs: String = "en",
        @Query("fields") fields: String = OffMapping.FIELDS,
    ): OffSearchResponse
}

/**
 * Barcode lookups use the product API; text search uses Search-a-licious, Open Food Facts' full-text
 * search service. Open Food Facts asks for a User-Agent naming the app and a contact.
 */
class OpenFoodFactsClient(
    userAgent: String,
    productBaseUrl: String = "https://world.openfoodfacts.org/",
    searchBaseUrl: String = "https://search.openfoodfacts.org/",
    now: () -> Long = System::currentTimeMillis,
) : OnlineFoodSource {
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val products = retrofit(productBaseUrl).create(ProductApi::class.java)
    private val searches = retrofit(searchBaseUrl).create(SearchApi::class.java)

    // Open Food Facts allows 15 product reads and 10 searches a minute per IP, and may ban an IP that
    // goes over, so stay under rather than retry.
    private val productLimit = RateLimiter(perMinute = 15, now = now)
    private val searchLimit = RateLimiter(perMinute = 10, now = now)

    override suspend fun byBarcode(barcode: String): FoodProduct? {
        val product = fetchProduct(barcode) ?: return null
        OffMapping.toFoodProduct(product)?.let { return it }
        // A known product whose calories aren't on the product page (removed in an edit, or never entered).
        // The search index can still hold them from an earlier version, which beats "not found".
        val indexed = searchIndex("code:$barcode", pageSize = 1).firstOrNull { it.code == barcode } ?: return null
        return OffMapping.toFoodProduct(product.copy(nutriments = indexed.nutriments))
    }

    override suspend fun details(barcode: String): ProductDetails? = fetchProduct(barcode)?.let(OffMapping::toDetails)

    override suspend fun search(query: String): List<FoodProduct> {
        // Search-a-licious reads the query as Lucene syntax; keep what's typed as plain words.
        val words = query.replace(Regex("""[:"()\[\]{}^~*?\\/!+\-]"""), " ").trim()
        if (words.isEmpty()) return emptyList()
        return searchIndex(words).mapNotNull(OffMapping::toFoodProduct)
    }

    private suspend fun fetchProduct(barcode: String): OffProduct? {
        productLimit.acquire()
        val response = try {
            products.product(barcode)
        } catch (e: HttpException) {
            if (e.code() == 404) return null
            throw IOException("Open Food Facts returned ${e.code()}", e)
        }
        // status 0 is "unknown or invalid code", sent with a 200.
        return response.product.takeIf { response.status == 1 }
    }

    private suspend fun searchIndex(query: String, pageSize: Int = 20): List<OffProduct> {
        searchLimit.acquire()
        return try {
            searches.search(query, pageSize).hits
        } catch (e: HttpException) {
            throw IOException("Open Food Facts search returned ${e.code()}", e)
        }
    }

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
