package com.ravan.SpringBootLab.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventRetentionCleanupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private EventRetentionCleanupService service;

    @BeforeEach
    void setUp() {
        service = spy(
                new EventRetentionCleanupService(
                        jdbcTemplate,
                        30,
                        90
                )
        );
    }

    @Test
    void scheduledCleanupShouldHandleProcessedEventDeletion() {
        doReturn(
                new EventRetentionCleanupService.CleanupResult(3, 0)
        ).when(service).deleteExpiredRecords();

        assertDoesNotThrow(service::scheduledCleanup);

        verify(service).deleteExpiredRecords();
    }

    @Test
    void scheduledCleanupShouldHandleAuditDeletionOnly() {
        doReturn(
                new EventRetentionCleanupService.CleanupResult(0, 2)
        ).when(service).deleteExpiredRecords();

        assertDoesNotThrow(service::scheduledCleanup);

        verify(service).deleteExpiredRecords();
    }

    @Test
    void scheduledCleanupShouldHandleNoDeletion() {
        doReturn(
                new EventRetentionCleanupService.CleanupResult(0, 0)
        ).when(service).deleteExpiredRecords();

        assertDoesNotThrow(service::scheduledCleanup);

        verify(service).deleteExpiredRecords();
    }
}
