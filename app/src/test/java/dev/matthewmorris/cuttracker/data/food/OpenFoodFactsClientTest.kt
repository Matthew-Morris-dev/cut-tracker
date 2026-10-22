package dev.matthewmorris.cuttracker.data.food

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OpenFoodFactsClientTest {
    private val server = MockWebServer()
    private lateinit var client: OpenFoodFactsClient

    @Before fun setUp() {
        server.start()
        val base = server.url("/").toString()
        client = OpenFoodFactsClient(userAgent = "CutTracker/test (https://example.com)", productBaseUrl = base, searchBaseUrl = base)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `barcode lookups send the user agent and ask only for the fields used`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status":1,"product":{"product_name":"Mars","nutriments":{"energy-kcal_100g":450}}}"""),
        )

        val product = client.byBarcode("5000159407236")

        assertEquals("Mars", product?.name)
        val request = server.takeRequest()
        assertEquals("CutTracker/test (https://example.com)", request.getHeader("User-Agent"))
        assertEquals("/api/v2/product/5000159407236", request.requestUrl?.encodedPath)
        assertEquals(OffMapping.FIELDS, request.requestUrl?.queryParameter("fields"))
    }

    @Test fun `an unknown barcode is not found, whether it comes back as 404 or status 0`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":0}"""))
        server.enqueue(MockResponse().setBody("""{"code":"00000017","status":0,"status_verbose":"no code or invalid code"}"""))

        assertNull(client.byBarcode("8008440222308"))
        assertNull(client.byBarcode("00000017"))
    }

    @Test fun `a product with no calories on its page takes them from the search index`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"product":{"code":"5000159551823","product_name":"Mars Bar","serving_quantity":39.4,
                    "nutriments":{"added-sugars_100g":0}}}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody("""{"hits":[{"code":"5000159551823","nutriments":{"energy-kcal_100g":442,"proteins_100g":4.5}}]}"""),
        )

        val product = client.byBarcode("5000159551823")!!

        assertEquals(442.0, product.kcalPer100g, 1e-9)
        assertEquals(39.4, product.servingG!!, 1e-9)
        server.takeRequest()
        assertEquals("code:5000159551823", server.takeRequest().requestUrl?.queryParameter("q"))
    }

    @Test fun `details come back even when the product has no calories`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":1,"product":{"product_name":"Mars Bar","serving_quantity":39.4,"product_quantity":157.6,
                    "categories_tags":["en:sweet-snacks"],"nutriments":{}}}""",
            ),
        )

        val details = client.details("5000159551823")!!

        assertEquals(157.6, details.packG!!, 1e-9)
        assertEquals("treat", details.suggestedTag)
    }

    @Test fun `a server error is an IOException so the screen can say try again`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching { client.byBarcode("123") }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test fun `search reads hits and sends what was typed as plain words`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"hits":[{"code":"1","product_name":"Greek yogurt","brands":["Asda"],"nutriments":{"energy-kcal_100g":66}},
                           {"code":"2","product_name":"No calories"}],"page":1}""",
            ),
        )

        val results = client.search("greek: yogurt")

        assertEquals(listOf("Greek yogurt"), results.map { it.name })
        val request = server.takeRequest()
        assertEquals("/search", request.requestUrl?.encodedPath)
        assertEquals("greek  yogurt", request.requestUrl?.queryParameter("q"))
    }
}
