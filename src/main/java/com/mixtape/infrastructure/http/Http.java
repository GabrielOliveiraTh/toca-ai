package com.mixtape.infrastructure.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Every client for an external API comes from here: timeouts and an own executor (see HttpStarvationTest).
public final class Http {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "http-client");
        thread.setDaemon(true);
        return thread;
    });

    private Http() {
    }

    public static RestClient client(String baseUrl) {
        return client(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    public static RestClient client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(EXECUTOR)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
