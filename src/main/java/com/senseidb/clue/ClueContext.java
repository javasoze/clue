package com.senseidb.clue;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;

import com.senseidb.clue.commands.ClueCommand;
import com.senseidb.clue.commands.DeleteCommand;
import com.senseidb.clue.commands.DirectoryCommand;
import com.senseidb.clue.commands.DocValCommand;
import com.senseidb.clue.commands.ExitCommand;
import com.senseidb.clue.commands.HelpCommand;
import com.senseidb.clue.commands.InfoCommand;
import com.senseidb.clue.commands.MergeCommand;
import com.senseidb.clue.commands.PostingsCommand;
import com.senseidb.clue.commands.ReadonlyCommand;
import com.senseidb.clue.commands.SearchCommand;
import com.senseidb.clue.commands.TermsCommand;

public class ClueContext {

  private final IndexReaderFactory readerFactory;
  private final SortedMap<String, ClueCommand> cmdMap;
  private final boolean interactiveMode;
  private IndexWriter writer;
  private final Directory directory;
  private boolean readOnlyMode;
  private final IndexWriterConfig writerConfig;
  
  public ClueContext(Directory directory, IndexReaderFactory readerFactory,
       IndexWriterConfig writerConfig, boolean interactiveMode){
    this.directory = directory;
    this.readerFactory = readerFactory;
    this.writerConfig = writerConfig;
    this.writer = null;
    this.interactiveMode = interactiveMode;
    this.cmdMap = new TreeMap<String, ClueCommand>();
    this.readOnlyMode = false;
    
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
    new ReadonlyCommand(this);
    new DirectoryCommand(this);
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
  
  public boolean isReadOnlyMode() {
    return readOnlyMode;
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
    if (readOnlyMode) return null;
    if (writer == null) {
      try {
        writer = new IndexWriter(directory, writerConfig);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return writer;
  }
  
  public Directory getDirectory() {
    return directory;
  }
  
  public void setReadOnlyMode(boolean readOnlyMode) {
    this.readOnlyMode = readOnlyMode;
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      writer = null;
    }
  }
  
  public void shutdown() throws IOException{
    try {
      readerFactory.shutdown();
    } 
    finally{
      if (writer != null) {
        writer.close();
      }
    }
  }
}
