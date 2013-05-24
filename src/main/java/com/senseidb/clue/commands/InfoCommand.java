package com.senseidb.clue.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;

import com.senseidb.clue.ClueContext;

public class InfoCommand extends ClueCommand {

  public InfoCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "info";
  }

  @Override
  public String help() {
    return "displays information about the index, <segment number> to get information on the segment";
  }

  private static String toString(Object[] info) throws IOException {
    FieldInfo finfo = (FieldInfo) info[0];
    List<Terms> termList = (List<Terms>) info[1];
    TreeMap<String, String> valMap = new TreeMap<String, String>();
    valMap.put("name", finfo.name);
    valMap.put("docval", String.valueOf(finfo.hasDocValues()));

    valMap.put("attributes", finfo.attributes().toString());
    if (finfo.hasDocValues()) {
      valMap.put("docval_type", String.valueOf(finfo.getDocValuesType()));
    } else {

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
        valMap.put("num_terms", String.valueOf(numTerms));
        valMap.put("doc_count", String.valueOf(docCount));
        valMap.put("sum_doc_freq", String.valueOf(sumDocFreq));
        valMap.put("sum_total_term_freq", String.valueOf(sumTotalTermFreq));
      }
    }

    return valMap.toString();
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    IndexReader r = ctx.getIndexReader();
    out.println("readonly mode: " + getContext().isReadOnlyMode());

    List<AtomicReaderContext> leaves = r.leaves();
    if (args.length == 0) {
      out.println("numdocs: " + r.numDocs());
      out.println("maxdoc: " + r.maxDoc());
      out.println("num deleted docs: " + r.numDeletedDocs());
      out.println("segment count: " + leaves.size());
      HashMap<String, Object[]> fields = new HashMap<String, Object[]>();
      for (AtomicReaderContext leaf : leaves) {
        AtomicReader ar = leaf.reader();
        FieldInfos fldInfos = ar.getFieldInfos();
        Iterator<FieldInfo> finfoIter = fldInfos.iterator();
        
        Fields flds = ar.fields();
        
        while (finfoIter.hasNext()) {
          FieldInfo finfo = finfoIter.next();
          Object[] data = fields.get(finfo.name);
          Terms t = flds.terms(finfo.name);
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
        out.println(toString(finfo));
      }
    } else {
      int segid;
      try {
        segid = Integer.parseInt(args[0]);
        if (segid < 0 || segid >= leaves.size()) {
          throw new IllegalArgumentException("invalid segment");
        }
      } catch (Exception e) {
        out.println("segment id must be a number betweem 0 and "
            + (leaves.size() - 1));
        return;
      }

      AtomicReaderContext leaf = leaves.get(segid);
      AtomicReader atomicReader = leaf.reader();

      out.println("segment " + segid + ": ");
      out.println("doc base: " + leaf.docBase);
      out.println("numdocs: " + atomicReader.numDocs());
      out.println("maxdoc: " + atomicReader.maxDoc());
      out.println("num deleted docs: " + atomicReader.numDeletedDocs());

      FieldInfos fields = atomicReader.getFieldInfos();
      Fields flds = atomicReader.fields();

      out.println("number of fields: " + fields.size());

      for (int i = 0; i < fields.size(); ++i) {
        FieldInfo finfo = fields.fieldInfo(i);
        Terms te = flds.terms(finfo.name);
        out.println(toString(new Object[] { finfo, Arrays.asList(te) }));
      }
    }

    out.flush();
  }

}
