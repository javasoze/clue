package com.senseidb.clue;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;

import com.senseidb.clue.commands.ClueCommand;
import com.senseidb.clue.commands.DeleteCommand;
import com.senseidb.clue.commands.DocValCommand;
import com.senseidb.clue.commands.ExitCommand;
import com.senseidb.clue.commands.HelpCommand;
import com.senseidb.clue.commands.InfoCommand;
import com.senseidb.clue.commands.MergeCommand;
import com.senseidb.clue.commands.PostingsCommand;
import com.senseidb.clue.commands.SearchCommand;
import com.senseidb.clue.commands.TermsCommand;

public class ClueContext {

  private final IndexReaderFactory readerFactory;
  private final SortedMap<String, ClueCommand> cmdMap;
  private final boolean interactiveMode;
  private final IndexWriter writer;
  
  public ClueContext(IndexReaderFactory readerFactory, IndexWriter writer, boolean interactiveMode){
    this.readerFactory = readerFactory;
    this.writer = writer;
    this.interactiveMode = interactiveMode;
    this.cmdMap = new TreeMap<String, ClueCommand>();
    
    // registers all the commands we currently support
    new HelpCommand(this);
    new ExitCommand(this);
    new InfoCommand(this);
    new DocValCommand(this);
    new SearchCommand(this);
    new TermsCommand(this);
    new PostingsCommand(this);
    new MergeCommand(this);
    new DeleteCommand(this);
  }
  
  public void registerCommand(ClueCommand cmd){
    String cmdName = cmd.getName();
    if (cmdMap.containsKey(cmdName)){
      throw new IllegalArgumentException(cmdName+" exists!");
    }
    cmdMap.put(cmdName, cmd);
  }
  
  public boolean isInteractiveMode(){
    return interactiveMode;
  }
  
  public ClueCommand getCommand(String cmd){
    return cmdMap.get(cmd);
  }
  
  public Map<String, ClueCommand> getCommandMap(){
    return cmdMap;
  }
  
  public IndexReader getIndexReader(){
    return readerFactory.getIndexReader();
  }
  
  public void refreshReader() throws IOException{
    readerFactory.refreshReader();
  }
  
  public IndexWriter getIndexWriter(){
    return writer;
  }
  
  public void shutdown() throws IOException{
    try {
      readerFactory.shutdown();
    } 
    finally{
      writer.close();
    }
  }
}
