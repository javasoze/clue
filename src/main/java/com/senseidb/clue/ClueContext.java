package com.senseidb.clue;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;

import com.senseidb.clue.commands.ClueCommand;
import com.senseidb.clue.commands.DocValCommand;
import com.senseidb.clue.commands.ExitCommand;
import com.senseidb.clue.commands.HelpCommand;
import com.senseidb.clue.commands.InfoCommand;
import com.senseidb.clue.commands.SearchCommand;
import com.senseidb.clue.commands.TermsCommand;

public class ClueContext {

  private final IndexReader idxReader;
  private final SortedMap<String, ClueCommand> cmdMap;
  
  public ClueContext(IndexReader idxReader){
    this.idxReader = idxReader;
    this.cmdMap = new TreeMap<String, ClueCommand>();
    
    // registers all the commands we currently support
    new HelpCommand(this);
    new ExitCommand(this);
    new InfoCommand(this);
    new DocValCommand(this);
    new SearchCommand(this);
    new TermsCommand(this);
  }
  
  public void registerCommand(ClueCommand cmd){
    String cmdName = cmd.getName();
    if (cmdMap.containsKey(cmdName)){
      throw new IllegalArgumentException(cmdName+" exists!");
    }
    cmdMap.put(cmdName, cmd);
  }
  
  public ClueCommand getCommand(String cmd){
    return cmdMap.get(cmd);
  }
  
  public Map<String, ClueCommand> getCommandMap(){
    return cmdMap;
  }
  
  public IndexReader getIndexReader(){
    return idxReader;
  }
  
  public void shutdown(){
    try {
      idxReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
