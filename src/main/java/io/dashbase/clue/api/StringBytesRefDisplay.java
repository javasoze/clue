package io.dashbase.clue.api;

public class StringBytesRefDisplay implements BytesRefDisplay {

    public StringBytesRefDisplay() {}

    @Override
    public BytesRefPrinter getBytesRefPrinter(String field) {
        return BytesRefPrinter.UTFPrinter;
    }

    public static final StringBytesRefDisplay INSTANCE = new StringBytesRefDisplay();
}
