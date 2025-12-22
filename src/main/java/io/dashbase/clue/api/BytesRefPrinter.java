package io.dashbase.clue.api;

import org.apache.lucene.util.BytesRef;

public interface BytesRefPrinter {
  String print(BytesRef bytesRef);
  
  public static BytesRefPrinter UTFPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
        try {
            return bytesRef.utf8ToString();
        } catch (Exception e) {
            return bytesRef.toString();
        }
    }
  };
  
  public static BytesRefPrinter RawBytesPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
      return bytesRef.toString();
    }
    
  };
}
