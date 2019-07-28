package io.dashbase.clue.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

@Readonly
public class ReconstructCommand extends ClueCommand {

  private final LuceneContext ctx;

  public ReconstructCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Override
  public String getName() {
    return "reconstruct";
  }

  @Override
  public String help() {
    return "reconstructs an indexed field for a document";
  }
  
  public String reconstructWithPositions(TermsEnum te, int docid, Bits liveDocs) throws IOException{
    TreeMap<Integer,List<String>> docTextMap = new TreeMap<Integer,List<String>>();
    BytesRef text;
    PostingsEnum postings = null;
    while ((text = te.next()) != null) {
      postings = te.postings(postings, PostingsEnum.FREQS | PostingsEnum.POSITIONS);
      int iterDoc = postings.advance(docid);
      if (iterDoc == docid) {
        int freq = postings.freq();
        for (int i = 0; i < freq; ++i) {
          int pos = postings.nextPosition();
          List<String> textList = docTextMap.get(pos);
          if (textList == null) {
            textList = new ArrayList<String>();
            docTextMap.put(pos, textList);
          }
          textList.add(text.utf8ToString());
        }
      }
    }
    StringBuilder buf = new StringBuilder();
    for (Entry<Integer, List<String>> entry : docTextMap.entrySet()) {
      Integer pos = entry.getKey();
      List<String> terms = entry.getValue();
      for (String term : terms) {
        buf.append(term+"("+pos+") ");
      }
    }
    return buf.toString();
  }

  public String reconstructNoPositions(TermsEnum te, int docid, Bits liveDocs) throws IOException{
    List<String> textList = new ArrayList<String>();
    BytesRef text;
    PostingsEnum postings = null;
    while ((text = te.next()) != null) {
      postings = te.postings(postings, PostingsEnum.FREQS);
      int iterDoc = postings.advance(docid);
      if (iterDoc == docid) {
        textList.add(text.utf8ToString());
      }
    }
    StringBuilder buf = new StringBuilder();
    for (String s : textList) {
      buf.append(s+" ");
    }
    return buf.toString();
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-f", "--field").required(true).help("field name");
    parser.addArgument("-d", "--doc").type(Integer.class).required(true).help("doc id");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    String field = args.getString("field");
    
    int doc = args.getInt("doc");
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    
    boolean found = false;
    
    
    for (LeafReaderContext ctx : leaves) {
      LeafReader atomicReader = ctx.reader();
      FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
      if (finfo == null) continue;
      
//      if (!finfo.isIndexed()) {
//        out.println(field+" is not an indexed field");
//        return;
//      }
      int docID = doc - ctx.docBase;
      if (docID >= 0) {
        Terms terms = atomicReader.terms(field);
        boolean hasPositions  = terms.hasPositions();
        
        TermsEnum te = terms.iterator();
        if (hasPositions) {
          out.println(reconstructWithPositions(te, docID, atomicReader.getLiveDocs()));
        }
        else {
          out.println(reconstructNoPositions(te, docID, atomicReader.getLiveDocs()));
        }
        found = true;
        break;
      }
    }
    
    if (!found) {
      out.println(doc + " not found");
      return;
    }
    
    
  }

}
