package io.dashbase.clue.api;

public class RawBytesRefDisplay implements BytesRefDisplay {

  public RawBytesRefDisplay() {
    
  }
  
  @Override
  public BytesRefPrinter getBytesRefPrinter(String field) {
    return BytesRefPrinter.RawBytesPrinter;
  }

  public static final RawBytesRefDisplay INSTANCE = new RawBytesRefDisplay();
}
