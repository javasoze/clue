package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import com.senseidb.clue.ClueContext;

public class ExplainCommand extends ClueCommand {

 
  public ExplainCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "explain";
  }

  @Override
  public String help() {
    return "shows score explanation of a doc";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 2) {
      out.println("usage: query docs");
      return;
    }
    
    
    String docString = args[args.length - 1];
    String[] docList = docString.split(",");
    
    List<Integer> docidList = new ArrayList<Integer>();
    try {
      for (String s : docList) {
        docidList.add(Integer.parseInt(s));
      }
    }
    catch(Exception e) {
      out.println("error in parsing docids: "+e.getMessage());
      return;
    }
    StringBuilder buf = new StringBuilder();
    
    for (int i=0; i<args.length-1;++i) {
      buf.append(args[i]).append(" ");
    }
    
    IndexReader r = ctx.getIndexReader();
    IndexSearcher searcher = new IndexSearcher(r);
    Query q = null;
    
    String qstring = buf.toString();
    
    try{
      q = ctx.getQueryBuilder().build(qstring);
    }
    catch(Exception e){
      out.println("cannot parse query: "+e.getMessage());
      return;
    }
    
    for (Integer docid : docidList) {
      Explanation expl = searcher.explain(q, docid);
      out.println(expl);
    }
    
    out.flush();
  }

}
