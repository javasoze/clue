package com.senseidb.clue.test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.BytesRef;

public class PayloadTokenizer extends Tokenizer {

  private final String[] tokens;
  
  private CharTermAttribute termAttr;
  private PayloadAttribute payloadAttr;
  private PositionIncrementAttribute positionAttr;
  private OffsetAttribute offsetAttr;
  private BytesRef payload;
  private int count = 0;
  private Iterator<String> iter = null;
  
  public PayloadTokenizer(String text)
          throws IOException {
    setReader(new StringReader(text));
    this.tokens = text.toLowerCase().split(",");
    
    termAttr = addAttribute(CharTermAttribute.class);
    termAttr.resizeBuffer(text.length()); // maximum size necessary is the size of the input
    payloadAttr = addAttribute(PayloadAttribute.class);
    payload = new BytesRef(new byte[4]);
    positionAttr = addAttribute(PositionIncrementAttribute.class);
    offsetAttr = addAttribute(OffsetAttribute.class);
  }

  private static BytesRef intToByteArray(int value,BytesRef reuse) {
    ByteBuffer.wrap(reuse.bytes).putInt(value);
    return reuse;
  }

  @Override
  public final boolean incrementToken() throws IOException {
    
    if (iter.hasNext()) {
      clearAttributes();
      
      // This is the dummy term.
      termAttr.setEmpty();
      termAttr.append(iter.next());
      payloadAttr.setPayload(intToByteArray(Float.floatToIntBits(this.tokens.length),payload));
      positionAttr.setPositionIncrement(1);
      offsetAttr.setOffset(0, count);
      count++;
      return true;
    }
    return false;
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    iter = Arrays.asList(this.tokens).iterator();
    count = 0;
  }

}
