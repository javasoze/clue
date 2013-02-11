package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
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

  @Override
  public void execute(String[] args, PrintStream out) throws Exception{
    IndexReader r = ctx.getIndexReader();
    List<AtomicReaderContext> leaves = r.leaves();
    if (args.length == 0){
      out.println("numdocs: " + r.numDocs());
      out.println("maxdoc: " + r.maxDoc());
      out.println("num deleted docs: " + r.numDeletedDocs());
      out.println("segment count: "+leaves.size());
      Set<String> fields = new HashSet<String>();
      for (AtomicReaderContext leaf : leaves){
        AtomicReader ar = leaf.reader();
        Fields flds = ar.fields();
        for (String f : flds){
          fields.add(f);
        }
      }
      out.println("number of fields: " + fields.size());
      Iterator<String> fieldNames = fields.iterator();
      while(fieldNames.hasNext()){
        out.println(fieldNames.next());
      }
    }
    else{
      int segid;
      try{
        segid = Integer.parseInt(args[0]);
        if (segid <0 || segid >= leaves.size()){
          throw new IllegalArgumentException("in valid segment");
        }
      }
      catch(Exception e){
        out.println("segment id must be a number betweem 0 and " + (leaves.size()-1));
        return;
      }
      
      AtomicReaderContext leaf = leaves.get(segid);
      AtomicReader atomicReader = leaf.reader();
      
      out.println("segment "+segid+": ");
      out.println("doc base: "+leaf.docBase);
      out.println("numdocs: " + atomicReader.numDocs());
      out.println("maxdoc: " + atomicReader.maxDoc());
      out.println("num deleted docs: " + atomicReader.numDeletedDocs());
     
      Fields fields = atomicReader.fields();
      Iterator<String> fieldNames = fields.iterator();
      
      out.println("number of fields: " + fields.size());
      
      while(fieldNames.hasNext()){
        out.println(fieldNames.next());
      }
    }

    out.flush();
  }

}
