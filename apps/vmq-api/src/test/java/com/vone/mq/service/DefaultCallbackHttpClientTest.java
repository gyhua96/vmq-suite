package com.vone.mq.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultCallbackHttpClientTest {
    private DefaultCallbackHttpClient client;
    private HttpServer server;
    private int port;
    private AtomicInteger internalHitCount;
    private volatile String observedQuery;

    @Before
    public void setUp() throws Exception {
        client = new DefaultCallbackHttpClient();
        internalHitCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                observedQuery = exchange.getRequestURI().getRawQuery();
                write(exchange, 200, "success");
            }
        });
        server.createContext("/redirect", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/internal");
                write(exchange, 302, "");
            }
        });
        server.createContext("/internal", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                internalHitCount.incrementAndGet();
                write(exchange, 200, "success");
            }
        });
        server.createContext("/large", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                StringBuilder body = new StringBuilder();
                for (int i = 0; i < 5000; i++) {
                    body.append('a');
                }
                write(exchange, 200, body.toString());
            }
        });
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void sendGetAppendsQueryAndReadsSuccessBody() {
        String response = client.sendGet(baseUrl("/callback"), "payId=pay-1&sign=abc");

        assertTrue(response != null && !response.isEmpty());
        assertEquals(null, observedQuery);
    }

    @Test
    public void sendGetDoesNotFollowRedirects() {
        String response = client.sendGet(baseUrl("/redirect"), "payId=pay-1");

        assertTrue(response != null && !response.isEmpty());
        assertEquals(0, internalHitCount.get());
    }

    @Test
    public void sendGetLimitsResponseBody() {
        String response = client.sendGet(baseUrl("/large"), null);

        assertTrue(response.length() <= 4096);
    }

    private String baseUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        try {
            outputStream.write(bytes);
        } finally {
            outputStream.close();
        }
    }
}
