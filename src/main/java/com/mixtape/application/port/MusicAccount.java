package com.mixtape.application.port;

public interface MusicAccount {
    boolean configured();

    boolean connected();

    String loginUrl();

    void handleCallback(String code, String state);

    void disconnect();
}
