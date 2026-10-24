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

    private com.waylo.crmservice.domain.Customer customer() {
        com.waylo.crmservice.domain.Customer c = new com.waylo.crmservice.domain.Customer();
        c.setEmail("ada@waylo.test"); c.setFirstName("Ada"); c.setLastName("L");
        c.setPhone("555"); c.setPassportNumber("P1");
        return c;
    }

    @Test
    void userDeleted_anonymizesKeepsRow_andIsIdempotent() {
        com.waylo.crmservice.domain.Customer c = customer();
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(c));
        String msg = "{\"eventType\":\"USER_DELETED\",\"userId\":\"" + userId + "\"}";

        listener().handleAuthEvent(rec(msg), ack);
        listener().handleAuthEvent(rec(msg), ack);

        org.junit.jupiter.api.Assertions.assertEquals("deleted+" + userId + "@deleted.invalid", c.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals("Silinmis", c.getFirstName());
        org.junit.jupiter.api.Assertions.assertNull(c.getPhone());
        org.junit.jupiter.api.Assertions.assertNull(c.getPassportNumber());
        org.junit.jupiter.api.Assertions.assertFalse(c.isActive());
        verify(repository, times(1)).save(c);
        verify(repository, never()).delete(any());
        verify(repository, never()).insertIfAbsent(any(), anyString(), anyString(), anyString());
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void userDeleted_missingCustomer_noop() {
        when(repository.findById(userId)).thenReturn(java.util.Optional.empty());
        listener().handleAuthEvent(rec("{\"eventType\":\"USER_DELETED\",\"userId\":\"" + userId + "\"}"), ack);
        verify(repository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void profileUpdated_updatesNamesAndEmail() {
        com.waylo.crmservice.domain.Customer c = customer();
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(c));
        listener().handleAuthEvent(rec(json("PROFILE_UPDATED", "Grace Brewster Hopper", "grace@waylo.test")), ack);
        org.junit.jupiter.api.Assertions.assertEquals("Grace", c.getFirstName());
        org.junit.jupiter.api.Assertions.assertEquals("Brewster Hopper", c.getLastName());
        org.junit.jupiter.api.Assertions.assertEquals("grace@waylo.test", c.getEmail());
        verify(repository).save(c);
        verify(repository, never()).insertIfAbsent(any(), anyString(), anyString(), anyString());
        verify(ack).acknowledge();
    }

    @Test
    void profileUpdated_missingCustomer_doesNotCreate() {
        when(repository.findById(userId)).thenReturn(java.util.Optional.empty());
        listener().handleAuthEvent(rec(json("PROFILE_UPDATED", "A B", "a@waylo.test")), ack);
        verify(repository, never()).save(any());
        verify(repository, never()).insertIfAbsent(any(), anyString(), anyString(), anyString());
        verify(ack).acknowledge();
    }

    @Test
    void profileUpdated_anonymizedCustomer_notResurrected() {
        com.waylo.crmservice.domain.Customer c = customer();
        c.setActive(false);
        when(repository.findById(userId)).thenReturn(java.util.Optional.of(c));
        listener().handleAuthEvent(rec(json("PROFILE_UPDATED", "A B", "a@waylo.test")), ack);
        verify(repository, never()).save(any());
    }

    @Test
    void malformedDeletePayload_ackedWithoutException() {
        listener().handleAuthEvent(rec("{\"eventType\":\"USER_DELETED\",\"userId\":\"nope\"}"), ack);
        verify(ack).acknowledge();
    }

    @Test
    void nonStringPayload_isSkippedAndAcked() {
        listener().handleAuthEvent(rec(new Object()), ack);

        verifyNoInteractions(repository);
        verify(ack).acknowledge();
    }
}
