package io.dashbase.clue.api;

import org.apache.lucene.util.BytesRef;

import java.nio.charset.StandardCharsets;

public interface BytesRefPrinter {
  String print(BytesRef bytesRef);

  static String toUtf8String(BytesRef bytesRef) {
    if (bytesRef == null) {
      return "";
    }
    return new String(bytesRef.bytes, bytesRef.offset, bytesRef.length, StandardCharsets.UTF_8);
  }
  
  public static BytesRefPrinter UTFPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
        return BytesRefPrinter.toUtf8String(bytesRef);
    }
  };
  
  public static BytesRefPrinter RawBytesPrinter = new BytesRefPrinter() {

    @Override
    public String print(BytesRef bytesRef) {
      if (bytesRef == null) {
        return "";
      }
      return bytesRef.toString();
    }
    
  };
}
