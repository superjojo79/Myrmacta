package com.flowforge.domain.notification.port.out;

public interface EmailPort {

    void send(String to, String subject, String body);
}
