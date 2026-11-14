package com.waylo.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushSenderTest {

    private static final String URL = "https://exp.host/--/api/v2/push/send";

    @Test
    void disabledByDefaultFlag() {
        assertThat(new ExpoPushSender(false, RestClient.create(URL)).isEnabled()).isFalse();
    }

    @Test
    void send_postsMessages_andReportsDeviceNotRegistered() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ExponentPushToken[a]")))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\"},{\"status\":\"error\",\"message\":\"x\","
                        + "\"details\":{\"error\":\"DeviceNotRegistered\"}}]}", MediaType.APPLICATION_JSON));
        ExpoPushSender sender = new ExpoPushSender(true, builder.build());

        Set<String> invalid = sender.send(List.of("ExponentPushToken[a]", "ExponentPushToken[b]"), "T", "B");

        assertThat(invalid).containsExactly("ExponentPushToken[b]");
        server.verify();
    }

    @Test
    void send_httpError_throws() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        ExpoPushSender sender = new ExpoPushSender(true, builder.build());
        assertThatThrownBy(() -> sender.send(List.of("t"), "T", "B")).isInstanceOf(RuntimeException.class);
    }
}
