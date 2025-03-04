package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

@Readonly
public class InfoCommand extends ClueCommand {

  private final LuceneContext ctx;

  public InfoCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Override
  public String getName() {
    return "info";
  }

  @Override
  public String help() {
    return "displays information about the index, <segment number> to get information on the segment";
  }

  @SuppressWarnings("unchecked")
  private static void toString(Object[] info, PrintStream out) throws IOException {
    FieldInfo finfo = (FieldInfo) info[0];
    List<Terms> termList = (List<Terms>) info[1];
    out.println("name:\t\t" + finfo.name);
    out.println("docval_type:\t" + finfo.getDocValuesType());
    out.println("norms:\t\t" + finfo.hasNorms());

    IndexOptions indexOptions = finfo.getIndexOptions();
    if (indexOptions != null) {
      out.println("index_options:\t" + finfo.getIndexOptions().name());
    }
    out.println("payloads:\t" + finfo.hasPayloads());
    out.println("term vectors:\t" + finfo.hasTermVectors());
    out.println("vectors:\t" + finfo.hasVectorValues());
    out.println("attributes:\t" + finfo.attributes().toString());
    if (termList != null) {

      long numTerms = 0L;
      long docCount = 0L;
      long sumDocFreq = 0L;
      long sumTotalTermFreq = 0L;

      for (Terms t : termList) {
        if (t != null) {
          numTerms += t.size();
          docCount += t.getDocCount();
          sumDocFreq += t.getSumDocFreq();
          sumTotalTermFreq += t.getSumTotalTermFreq();
        }
      }
      if (numTerms < 0) {
        numTerms = -1;
      }
      if (docCount < 0) {
        docCount = -1;
      }
      if (sumDocFreq < 0) {
        sumDocFreq = -1;
      }
      if (sumTotalTermFreq < 0) {
        sumTotalTermFreq = -1;
      }
      out.println("num_terms:\t" + numTerms);
      out.println("doc_count:\t" + docCount);
      out.println("sum_doc_freq:\t" + sumDocFreq);
      out.println("sum_total_term_freq:\t" + sumTotalTermFreq);
    }
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-s", "--seg").type(Integer.class)
            .setDefault(-1).help("segment id");
    return parser;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void execute(Namespace args, PrintStream out) throws Exception {
    IndexReader r = ctx.getIndexReader();
    
    out.println("readonly mode: " + getContext().isReadOnlyMode());

    if (r instanceof DirectoryReader) {
      DirectoryReader dr = (DirectoryReader)r;
      SegmentInfos sis = SegmentInfos.readLatestCommit(dr.directory()); // read infos from dir
      for (SegmentCommitInfo commitInfo : sis) {
        if (commitInfo != null) {          
          out.println("Codec found: " + commitInfo.info.getCodec().getName());
          break;
        }
      }
    }
    
    List<LeafReaderContext> leaves = r.leaves();
    int segid = args.getInt("seg");
    if (segid < 0) {
      out.println("numdocs: " + r.numDocs());
      out.println("maxdoc: " + r.maxDoc());
      out.println("num deleted docs: " + r.numDeletedDocs());
      out.println("segment count: " + leaves.size());
      SortedMap<String, Object[]> fields = new TreeMap<String, Object[]>();
      for (LeafReaderContext leaf : leaves) {        
        try (LeafReader ar = leaf.reader()) {
          FieldInfos fldInfos = ar.getFieldInfos();

          for (var finfo : fldInfos) {
            Object[] data = fields.get(finfo.name);
            Terms t = ar.terms(finfo.name);
            if (data == null) {
              data = new Object[2];
              LinkedList<Terms> termsList = new LinkedList<Terms>();
              termsList.add(t);
              data[0] = finfo;
              data[1] = termsList;
              fields.put(finfo.name, data);
            } else {
              List<Terms> termsList = (List<Terms>) data[1];
              termsList.add(t);
            }
          }
        }
      }
      out.println("number of fields: " + fields.size());
      
      for (Object[] finfo : fields.values()) {
        FieldInfo f = (FieldInfo) finfo[0];
        out.println("=================================== Field "+f.name+" ===================================");
        toString(finfo, out);
      }
    } else {
      LeafReaderContext leaf = leaves.get(segid);
      try (LeafReader atomicReader = leaf.reader()) {

        out.println("segment " + segid + ": ");
        out.println("doc base:\t" + leaf.docBase);
        out.println("numdocs:\t" + atomicReader.numDocs());
        out.println("maxdoc:\t" + atomicReader.maxDoc());
        out.println("num deleted docs:\t" + atomicReader.numDeletedDocs());

        FieldInfos fields = atomicReader.getFieldInfos();

        out.println("number of fields: " + fields.size());

        for (int i = 0; i < fields.size(); ++i) {
          FieldInfo finfo = fields.fieldInfo(i);
          Terms te = atomicReader.terms(finfo.name);
          out.println("=================================== Field " + finfo.name + " ===================================");
          toString(new Object[]{finfo, Collections.singletonList(te)}, out);
        }
      }
    }
    out.flush();
  }

}
