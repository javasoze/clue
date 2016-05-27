package com.senseidb.clue.api;

import org.apache.lucene.util.BytesRef;

public interface BytesRefPrinter {  
  
  public static BytesRefPrinter UTFPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
      return bytesRef.utf8ToString();
    }
    
  };
  
  public static BytesRefPrinter RawBytesPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
      return bytesRef.toString();
    }
    
  };
  
  String print(BytesRef bytesRef);
  
}
