/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.product.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMessageTrackerTest {
    private static final String LOG_MESSAGE = "This is a log message.";
    private static final String NOT_LOGGED_MESSAGE = "This is not a log message.";

    private static final Pattern MESSAGE_REGEX = Pattern.compile("^This.*");
    private static final Pattern NOT_MESSAGE_REGEX = Pattern.compile("^dummy");

    private final LogMessageTracker logMessageTracker = new LogMessageTracker(this.getClass());
    private final Logger log = (Logger) LoggerFactory.getLogger(this.getClass());

    @Nested
    class WithoutTurboFilters {
        @BeforeEach
        void setup() {
            log.getLoggerContext().resetTurboFilterList();
            logMessageTracker.startTracking();
            log.trace(LOG_MESSAGE);
            log.debug(LOG_MESSAGE);
            log.info(LOG_MESSAGE);
            log.warn(LOG_MESSAGE);
            log.error(LOG_MESSAGE);
        }

        @AfterEach
        void cleanUp() {
            logMessageTracker.stopTracking();
        }

        @Test
        void givenLogsAndLevelFilter_whenSearch_thenFindLogs() {
            assertEquals(5, logMessageTracker.search(LOG_MESSAGE).size());
            assertEquals(1, logMessageTracker.search(LOG_MESSAGE, Level.INFO).size());

            assertEquals(5, logMessageTracker.search(MESSAGE_REGEX).size());
            assertEquals(1, logMessageTracker.search(MESSAGE_REGEX, Level.INFO).size());
        }

        @Test
        void givenLogsAndLevelFilter_whenSearch_thenFindNoLogs() {
            assertEquals(0, logMessageTracker.search(NOT_LOGGED_MESSAGE).size());
            assertEquals(0, logMessageTracker.search(LOG_MESSAGE, Level.ALL).size());

            assertEquals(0, logMessageTracker.search(NOT_MESSAGE_REGEX).size());
            assertEquals(0, logMessageTracker.search(MESSAGE_REGEX, Level.ALL).size());
        }

        @Test
        void givenLogsAndLevelFilter_whenCheckExist_thenLogsExist() {
            assertTrue(logMessageTracker.contains(LOG_MESSAGE));
            assertTrue(logMessageTracker.contains(LOG_MESSAGE, Level.TRACE));

            assertTrue(logMessageTracker.contains(MESSAGE_REGEX));
            assertTrue(logMessageTracker.contains(MESSAGE_REGEX, Level.TRACE));
        }

        @Test
        void givenLogsAndLevelFilter_whenCheckExist_thenLogsDontExist() {
            assertFalse(logMessageTracker.contains(NOT_LOGGED_MESSAGE));
            assertFalse(logMessageTracker.contains(LOG_MESSAGE, Level.ALL));

            assertFalse(logMessageTracker.contains(NOT_MESSAGE_REGEX));
            assertFalse(logMessageTracker.contains(MESSAGE_REGEX, Level.ALL));
        }

        @Test
        void givenLogs_whenGetLogs_thenAllLogsFound() {
            assertEquals(5, logMessageTracker.countEvents());

            List<ILoggingEvent> logEvents = logMessageTracker.getAllLoggedEvents();
            logEvents.forEach(event -> assertEquals(LOG_MESSAGE, event.getFormattedMessage()));

            assertEquals(1, logMessageTracker.getAllLoggedEventsWithLevel(Level.WARN).size());
        }

        @Test
        void givenFormattedLog_whenLookForLog_thenFindFormattedLog() {
            log.info("This is a {} log message.", "formatted");
            assertEquals(6, logMessageTracker.countEvents());
            assertTrue(logMessageTracker.contains("This is a formatted log message."));
            assertEquals(1, logMessageTracker.search("This is a formatted log message.").size());
        }

        @Test
        void givenLogTracker_whenCleanUp_thenLogsReleasedFromTracking() {
            // Test LogMessageTracker doesn't keep logs after running LogMessageTracker.clear in @AfterEach method
            assertEquals(5, logMessageTracker.countEvents());
        }
    }

    @Nested
    class WithDuplicateMessageTurboFilter {
        ApimlDuplicateMessagesFilter duplicateMessagesFilter = new ApimlDuplicateMessagesFilter();

        @BeforeEach
        void setup() {
            duplicateMessagesFilter.setAllowedRepetitions(0);
            duplicateMessagesFilter.start();

            log.getLoggerContext().addTurboFilter(duplicateMessagesFilter);
            logMessageTracker.startTracking();
        }

        @AfterEach
        void cleanUp() {
            logMessageTracker.stopTracking();

            // Stop all registered TurboFilters and then clear the list
            log.getLoggerContext().resetTurboFilterList();
        }

        @Test
        void whenLoggingMessage_testApimlDuplicateMessagesFilterWorksAsExpected() {
            RuntimeException exception = new RuntimeException("my exception");

            // formattedMessage = "Message"
            log.info("Message"); // NEUTRAL

            // formattedMessage = "Message"
            log.info("Message", new Object[]{"argument"}); // DENY

            // formattedMessage = "Message 1"
            log.warn(String.format("Message %s", 1)); // NEUTRAL

            // formattedMessage = "Message 1"
            log.warn("Message {}", new Object[]{1}); // DENY

            // formattedMessage = "Message 1\njava.lang.RuntimeException: my exception\n...."
            log.warn("Message 1", exception); // NEUTRAL

            // formattedMessage = "Message 1\njava.lang.RuntimeException: my exception\n...."
            log.error("Message {}", new Object[]{1, exception}); // DENY

            // formattedMessage = "Message [1]\njava.lang.RuntimeException: my exception\n...."
            log.error("Message {}", new Object[]{1}, exception); // NEUTRAL

            // formattedMessage = "Message [1, java.lang.RuntimeException: Exception inside formatted message] {}\njava.lang.RuntimeException: my exception\n...."
            log.error("Message {} {}", new Object[]{1, new RuntimeException("Exception inside formatted message")}, exception); // NEUTRAL

            assertEquals(5, logMessageTracker.getAllLoggedEvents().size()); // Only messages passing the DuplicateMessageFilter are logged
            assertEquals(1, logMessageTracker.getAllLoggedEventsWithLevel(Level.INFO).size());
            assertEquals(2, logMessageTracker.getAllLoggedEventsWithLevel(Level.WARN).size());
            assertEquals(2, logMessageTracker.getAllLoggedEventsWithLevel(Level.ERROR).size());
        }
    }
}
