package org.nsh07.wikireader.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class HostSelectionInterceptorTest {

    /**
     * Runs [request] through [interceptor] using a real OkHttp chain, terminated by an interceptor
     * that answers instead of hitting the network, and returns the request it would have sent.
     */
    private fun send(interceptor: HostSelectionInterceptor, request: Request): Request {
        val sent = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                sent.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        client.newCall(request).execute().close()
        return sent.get()
    }

    private fun request(host: String? = null) = Request.Builder()
        .url("https://en.wikipedia.org/wiki/Earth")
        .apply { if (host != null) header(HostSelectionInterceptor.HOST_HEADER, host) }
        .build()

    @Test
    fun withoutAHeader_theSharedHostIsUsed() {
        val interceptor = HostSelectionInterceptor()
        interceptor.setHost("de.wikipedia.org")

        assertEquals("de.wikipedia.org", send(interceptor, request()).url.host)
    }

    @Test
    fun aRequestHeader_overridesTheSharedHost() {
        val interceptor = HostSelectionInterceptor()
        interceptor.setHost("de.wikipedia.org")

        // Saving an article in another language must not follow whatever the reader switched to.
        assertEquals("fr.wikipedia.org", send(interceptor, request("fr.wikipedia.org")).url.host)
    }

    @Test
    fun theHeaderIsNotSentOverTheWire() {
        val interceptor = HostSelectionInterceptor()
        interceptor.setHost("en.wikipedia.org")

        val sent = send(interceptor, request("kn.wikipedia.org"))
        assertNull(sent.header(HostSelectionInterceptor.HOST_HEADER))
    }

    @Test
    fun changingTheSharedHostDoesNotAffectAPinnedRequest() {
        val interceptor = HostSelectionInterceptor()
        interceptor.setHost("en.wikipedia.org")
        val pinned = request("kn.wikipedia.org")

        interceptor.setHost("hi.wikipedia.org")

        assertEquals("kn.wikipedia.org", send(interceptor, pinned).url.host)
        assertEquals("hi.wikipedia.org", send(interceptor, request()).url.host)
    }

    @Test
    fun withNoHostAtAll_theUrlIsUntouched() {
        assertEquals("en.wikipedia.org", send(HostSelectionInterceptor(), request()).url.host)
    }
}
