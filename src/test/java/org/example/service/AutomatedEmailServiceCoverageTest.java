package org.example.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomatedEmailServiceCoverageTest {
    private final AutomatedEmailService service = AutomatedEmailService.getInstance();

    @Test
    void commandWritesCrLfAndConsumesMultilineSmtpResponse() throws Exception {
        StringWriter sink = new StringWriter();
        BufferedWriter writer = new BufferedWriter(sink);
        BufferedReader reader = new BufferedReader(new StringReader("250-localhost ready\r\n250 ok\r\n"));

        invoke("command",
                new Class<?>[]{BufferedWriter.class, BufferedReader.class, String.class},
                writer,
                reader,
                "HELO localhost");

        assertEquals("HELO localhost\r\n", sink.toString());
    }

    @Test
    void readResponseReturnsFinalLineAndFailsWhenServerClosesConnection() throws Exception {
        Object response = invoke("readResponse",
                new Class<?>[]{BufferedReader.class},
                new BufferedReader(new StringReader("354-continue\r\n354 send data\r\n")));

        assertEquals("354 send data", response);

        IOException failure = assertThrows(IOException.class, () -> invoke("readResponse",
                new Class<?>[]{BufferedReader.class},
                new BufferedReader(new StringReader(""))));
        assertEquals("SMTP server closed the connection.", failure.getMessage());

        IOException multilineFailure = assertThrows(IOException.class, () -> invoke("readResponse",
                new Class<?>[]{BufferedReader.class},
                new BufferedReader(new StringReader("250-still waiting\r\n"))));
        assertEquals("SMTP server closed the connection.", multilineFailure.getMessage());
    }

    @Test
    void hasTextAndSmtpPortHandleBlankValuesAndDefaults() throws Exception {
        assertFalse((Boolean) invoke("hasText", new Class<?>[]{String.class}, (Object) null));
        assertFalse((Boolean) invoke("hasText", new Class<?>[]{String.class}, " \t"));
        assertTrue((Boolean) invoke("hasText", new Class<?>[]{String.class}, " smtp.local "));

        int port = (Integer) invoke("smtpPort", new Class<?>[]{});
        assertTrue(port > 0);
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = AutomatedEmailService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
