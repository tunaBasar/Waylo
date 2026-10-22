package com.waylo.crmservice.listener;

import com.waylo.crmservice.dto.CustomerMapper;
import com.waylo.crmservice.repository.CustomerRepository;
import com.waylo.crmservice.service.CustomerServiceImpl;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises AuthEventListener together with the real CustomerServiceImpl so the name split and
 * email normalisation are verified at the repository boundary, not just mocked through.
 */
@ExtendWith(MockitoExtension.class)
class AuthEventListenerTest {

    @Mock
    private CustomerRepository repository;
    @Mock
    private CustomerMapper mapper;
    @Mock
    private Acknowledgment ack;

    private final UUID userId = UUID.randomUUID();

    private AuthEventListener listener() {
        return new AuthEventListener(new CustomerServiceImpl(repository, mapper), repository);
    }

    private static ConsumerRecord<String, Object> rec(Object value) {
        return new ConsumerRecord<>("auth-events", 0, 7L, "key", value);
    }

    private String json(String type, String name, String email) {
        return "{\"eventType\":\"" + type + "\",\"userId\":\"" + userId + "\",\"name\":\"" + name
                + "\",\"email\":\"" + email + "\",\"token\":\"t\"}";
    }

    @Test
    void verificationEvent_createsCustomerWithAuthUserIdAndSplitName() {
        when(repository.insertIfAbsent(any(), anyString(), anyString(), anyString())).thenReturn(1);

        listener().handleAuthEvent(rec(json("VERIFICATION", "Ada Mary Lovelace", " Ada@Waylo.TEST ")), ack);

        verify(repository).insertIfAbsent(userId, "Ada Mary", "Lovelace", "ada@waylo.test");
        verify(ack).acknowledge();
    }

    @Test
    void singleWordName_usesItAsFirstNameAndEmptyLastName() {
        when(repository.insertIfAbsent(any(), anyString(), anyString(), anyString())).thenReturn(1);

        listener().handleAuthEvent(rec(json("WELCOME", "Cher", "cher@waylo.test")), ack);

        verify(repository).insertIfAbsent(userId, "Cher", "", "cher@waylo.test");
    }

    @Test
    void repeatedEvent_insertsNothingAndDoesNotFail() {
        when(repository.insertIfAbsent(any(), anyString(), anyString(), anyString())).thenReturn(0);
        when(repository.existsById(userId)).thenReturn(true);

        listener().handleAuthEvent(rec(json("PASSWORD_RESET", "Ada Lovelace", "ada@waylo.test")), ack);

        verify(repository).insertIfAbsent(eq(userId), anyString(), anyString(), anyString());
        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void missingUserId_isSkippedAndAcked() {
        listener().handleAuthEvent(rec("{\"eventType\":\"VERIFICATION\",\"email\":\"x@waylo.test\"}"), ack);

        verifyNoInteractions(repository);
        verify(ack).acknowledge();
    }

    @Test
    void malformedJson_isSwallowedAndAcked() {
        listener().handleAuthEvent(rec("not-json"), ack);

        verifyNoInteractions(repository);
        verify(ack).acknowledge();
    }

    @Test
    void nonStringPayload_isSkippedAndAcked() {
        listener().handleAuthEvent(rec(new Object()), ack);

        verifyNoInteractions(repository);
        verify(ack).acknowledge();
    }
}
