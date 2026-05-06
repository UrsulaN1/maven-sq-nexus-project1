package com.mycompany.app;

import java.util.logging.Logger;

/**
 * Hello world!
 */
public class App {

    private static final Logger logger = Logger.getLogger(App.class.getName());
    private static final String MESSAGE = "Hello World!";

    // Private constructor prevents instantiation (satisfies SonarQube)
    private App() {
        throw new UnsupportedOperationException("This is a utility class");
    }

    public static void main(String[] args) {
        // Now calling the static method
        logger.info(getMessage());
    }

    // ADD 'static' HERE so the test can call it directly
    public static String getMessage() {
        return MESSAGE;
    }
}