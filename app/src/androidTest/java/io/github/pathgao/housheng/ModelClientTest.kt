package io.github.pathgao.housheng

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class ModelClientTest {
    private fun withResponse(delay: Long = 0, status: Int = 200, response: (String) -> String, test: (LoopbackModelClient) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2000
            val worker = thread {
                runCatching {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        val input = DataInputStream(socket.getInputStream())
                        var length = 0
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(":").trim().toInt()
                        }
                        check(length in 1..24000)
                        val bytes = ByteArray(length).also { input.readFully(it) }
                        val id = JSONObject(String(bytes, Charsets.UTF_8)).getString("request_id")
                        Thread.sleep(delay)
                        val body = response(id).toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
            try { test(LoopbackModelClient(server.localPort, timeoutMs = 500)) } finally { server.close(); worker.join(3000) }
            assertFalse("test server must stop", worker.isAlive)
        }
    }
    private fun result(id: String, decision: String) = JSONObject().put("request_id", id).put("decision", decision).toString()
    private fun rejects(client: LoopbackModelClient) { assertTrue(runCatching { client.classify("合成内容") }.isFailure) }

    @Test fun typedChoicesRoundTrip() {
        for (choice in listOf("keep", "filter", "uncertain")) {
            withResponse(response = { result(it, choice) }) { assertEquals(choice, it.classify("合成内容")) }
        }
    }
    @Test fun mismatchedOrMalformedResultsAreRejected() {
        for (response in listOf<(String) -> String>({ result("wrong-request", "filter") },
            { result(it, "invented") }, { "not-json" }, { "x".repeat(4097) })) {
            withResponse(response = response) { rejects(it) }
        }
    }
    @Test fun httpErrorsAndDisconnectAreRejected() {
        withResponse(status = 503, response = { result(it, "filter") }) { rejects(it) }
        val port = ServerSocket(0).use { it.localPort }
        rejects(LoopbackModelClient(port))
    }
    @Test fun slowFilterResponseExpires() {
        withResponse(delay = 700, response = { result(it, "filter") }) {
            val start = Session.now()
            rejects(it)
            assertTrue("request should expire before delayed response", Session.now() - start < 650)
        }
    }
    @Test fun realClefOnSelectedTransport() {
        val start = Session.now()
        val choice = ModelValidation.classify(InstrumentationRegistry.getInstrumentation().targetContext, "这条消息必须转发二十个群，不转发的家庭一定会遭灾！")
        assertEquals("filter", choice)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("modelRoundtrip", "$choice · ${Session.now() - start}ms")
        })
    }
}
