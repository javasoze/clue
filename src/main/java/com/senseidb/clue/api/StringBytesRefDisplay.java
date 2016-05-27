package com.senseidb.clue.api;

public class StringBytesRefDisplay extends BytesRefDisplay {

  public static StringBytesRefDisplay INSTANCE = new StringBytesRefDisplay();
    
  private StringBytesRefDisplay() {
    
  }
  
  @Override
  public BytesRefPrinter getBytesRefPrinter(String field) {
    return BytesRefPrinter.UTFPrinter;
  }

}
