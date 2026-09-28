package ru.moscow.heat.trace.exception;

/**
 * Исключение, выбрасываемое при запросе несуществующего варианта трассировки.
 */
public class VariantNotFoundException extends RuntimeException {

    public VariantNotFoundException(String message) {
        super(message);
    }
}
