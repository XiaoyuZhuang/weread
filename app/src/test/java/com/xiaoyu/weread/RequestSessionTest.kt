package com.xiaoyu.weread

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RequestSessionTest {
    @Test fun cancellationBeforeAttachmentCancelsLateCall() {
        val session = RequestSession()
        session.cancel()
        var cancelled = false
        try {
            session.attach { cancelled = true }
            fail("Cancelled session accepted a new HTTP stage")
        } catch (_: CancellationException) {}
        assertTrue(cancelled)
    }

    @Test fun cancellationBetweenStagesPreventsNextStage() {
        val session = RequestSession()
        var cancellations = 0
        session.attach { cancellations++ }
        session.detach()
        session.cancel()
        session.cancel()
        assertEquals(0, cancellations)
        try {
            session.checkActive()
            fail("Cancelled question continued")
        } catch (_: CancellationException) {}
    }

    @Test fun blockedHttpIsCancelledAndReplacementCompletes() {
        val server = MockWebServer()
        val pool = Executors.newCachedThreadPool()
        val client = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody("new question"))
            val old = RequestSession()
            val oldCall = client.newCall(Request.Builder().url(server.url("/old")).build())
            old.attach { oldCall.cancel() }
            val waiting = pool.submit<Boolean> {
                try { oldCall.execute().use { it.body?.string() }; false }
                catch (_: IOException) { true }
                finally { old.detach() }
            }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            old.cancel()
            val replacement = RequestSession()
            val newCall = client.newCall(Request.Builder().url(server.url("/new")).build())
            replacement.attach { newCall.cancel() }
            val result = pool.submit<String> {
                newCall.execute().use { it.body!!.string() }
            }
            assertEquals("new question", result.get(3, TimeUnit.SECONDS))
            assertTrue(waiting.get(3, TimeUnit.SECONDS))
            assertFalse(replacement.isCancelled)
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            pool.shutdownNow()
            server.shutdown()
        }
    }
}
