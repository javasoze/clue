package com.senseidb.clue.api;

public class RawBytesRefDisplay extends BytesRefDisplay {

  public static RawBytesRefDisplay INSTANCE = new RawBytesRefDisplay();
    
  private RawBytesRefDisplay() {
    
  }
  
  @Override
  public BytesRefPrinter getBytesRefPrinter(String field) {
    return BytesRefPrinter.RawBytesPrinter;
  }

}
