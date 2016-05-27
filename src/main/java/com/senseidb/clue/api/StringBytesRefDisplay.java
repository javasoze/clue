package com.senseidb.clue.api;

public class StringBytesRefDisplay extends BytesRefDisplay {

  private StringBytesRefDisplay() {
    
  }
  
  @Override
  public BytesRefPrinter getBytesRefPrinter(String field) {
    return BytesRefPrinter.UTFPrinter;
  }
  
  public static final StringBytesRefDisplay INSTANCE = new StringBytesRefDisplay();

}
