package com.mycompany.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {

    @Test
    public void testGetMessage() {
        // FIX: Call the method via the class name, NOT 'new App()'
        assertEquals("Hello World!", App.getMessage());
    }
}
