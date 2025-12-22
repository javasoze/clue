package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Readonly
@Command(name = "info", mixinStandardHelpOptions = true)
public class InfoCommand extends ClueCommand {

  private final LuceneContext ctx;

  public InfoCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-s", "--seg"}, defaultValue = "-1", description = "segment id")
  private int seg;

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
      Map<String, AtomicLong> docFreqStats = new TreeMap<>();
      Map<String, AtomicLong> sumTotalTermsFreq = new TreeMap<>();

      long docCount = 0L;

      for (Terms t : termList) {
        if (t != null) {
            docCount += t.getDocCount();
            TermsEnum termsEnum = t.iterator();
            if (termsEnum != null) {
              BytesRef term;
              while ((term = termsEnum.next()) != null) {
                  String termStr = safeTermToString(term);
                  AtomicLong count = docFreqStats.get(termStr);
                  if (count == null) {
                    count = new AtomicLong(0);
                    docFreqStats.put(termStr, count);
                  }
                  count.addAndGet(termsEnum.docFreq());

                count = sumTotalTermsFreq.get(termStr);
                if (count == null) {
                  count = new AtomicLong(0);
                  sumTotalTermsFreq.put(termStr, count);
                }
                count.addAndGet(termsEnum.totalTermFreq());
              }
          }
        }
      }


      long numTerms = docFreqStats.size();
      final AtomicLong sumDocFreq = new AtomicLong(0L);
      docFreqStats.values().forEach(num -> sumDocFreq.addAndGet(num.get()));

      final AtomicLong sumTotalTermFreq = new AtomicLong(0L);
      sumTotalTermsFreq.values().forEach(num -> sumTotalTermFreq.addAndGet(num.get()));

      out.println("num_terms:\t" + numTerms);
      out.println("doc_count:\t" + docCount);
      out.println("sum_doc_freq:\t" + sumDocFreq);
      out.println("sum_total_term_freq:\t" + sumTotalTermFreq);
    }
  }

  private static String safeTermToString(BytesRef term) {
    return BytesRefPrinter.toUtf8String(term);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void run(PrintStream out) throws Exception {
    IndexReader r = ctx.getIndexReader();
    
    out.println("readonly mode: " + getContext().isReadOnlyMode());

    if (r instanceof DirectoryReader) {
      DirectoryReader dr = (DirectoryReader)r;
      SegmentInfos sis = SegmentInfos.readLatestCommit(dr.directory()); // read infos from dir

      // assuming codecs are the same for all segments
      for (SegmentCommitInfo commitInfo : sis) {
        if (commitInfo != null) {          
          out.println("Codec found: " + commitInfo.info.getCodec().getName());
          break;
        }
      }
    }
    
    List<LeafReaderContext> leaves = r.leaves();
    int segid = seg;
    if (segid < 0) {
      out.println("numdocs: " + r.numDocs());
      out.println("maxdoc: " + r.maxDoc());
      out.println("num deleted docs: " + r.numDeletedDocs());
      out.println("segment count: " + leaves.size());
      SortedMap<String, Object[]> fields = new TreeMap<String, Object[]>();
      for (LeafReaderContext leaf : leaves) {        
        LeafReader ar = leaf.reader();
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
      out.println("number of fields: " + fields.size());
      
      for (Object[] finfo : fields.values()) {
        FieldInfo f = (FieldInfo) finfo[0];
        out.println("=================================== Field "+f.name+" ===================================");
        toString(finfo, out);
      }
    } else {
      LeafReaderContext leaf = leaves.get(segid);
      LeafReader atomicReader = leaf.reader();


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
    out.flush();
  }
}
