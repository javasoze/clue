package com.senseidb.clue;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import com.senseidb.clue.api.DefaultDirectoryBuilder;
import com.senseidb.clue.api.DefaultIndexReaderFactory;
import com.senseidb.clue.api.DefaultQueryBuilder;
import com.senseidb.clue.api.DirectoryBuilder;
import com.senseidb.clue.api.IndexReaderFactory;
import com.senseidb.clue.api.QueryBuilder;

public class ClueConfiguration {
  
  private static final String CLUE_CONF_FILE = "clue.conf";
  private static final String DIRECTORY_BUILDER_PARAM = "directory.builder";
  private static final String ANALYZER_QUERY_PARAM = "analyzer.query";
  private static final String INDEX_READER_FACTORY_PARAM = "indexreader.factory";
  private static final String QUERY_BUILDER_PARAM = "query.builder";
  
  private final Analyzer analyzerQuery;
  private final DirectoryBuilder dirBuilder;
  private final QueryBuilder queryBuilder;
  private final IndexReaderFactory indexReaderFactory;
  
  private static <T> T getInstance(String className, T defaultInstance) {
    try {
      if (className == null) {
        return defaultInstance;
      }
      T obj = (T) (Class.forName(className).newInstance());
      return obj;
    } catch (Exception e) {
      System.out.println("unable to obtain instance of class: " + className);
      return defaultInstance;
    }
  }

  private ClueConfiguration(Properties config) {    
    analyzerQuery = getInstance(config.getProperty(ANALYZER_QUERY_PARAM), 
        new StandardAnalyzer(Version.LUCENE_45));    
    dirBuilder = getInstance(config.getProperty(DIRECTORY_BUILDER_PARAM),
        new DefaultDirectoryBuilder());
    queryBuilder = getInstance(config.getProperty(QUERY_BUILDER_PARAM),
        new DefaultQueryBuilder());
    indexReaderFactory = getInstance(config.getProperty(INDEX_READER_FACTORY_PARAM),
        new DefaultIndexReaderFactory());
    
    System.out.println("Analyzer: \t\t" + analyzerQuery.getClass());
    System.out.println("Query Builder: \t\t" + queryBuilder.getClass());
    System.out.println("Directory Builder: \t" + dirBuilder.getClass());
    System.out.println("IndexReader Factory: \t" + indexReaderFactory.getClass());
  }

  public Analyzer getAnalyzerQuery() {
    return analyzerQuery;
  }

  public DirectoryBuilder getDirBuilder() {
    return dirBuilder;
  }

  public QueryBuilder getQueryBuilder() {
    return queryBuilder;
  }

  public IndexReaderFactory getIndexReaderFactory() {
    return indexReaderFactory;
  }

  public static ClueConfiguration load() throws IOException {
    String confDirPath = System.getProperty("config.dir");
    if (confDirPath == null) {      
      confDirPath = "config";
    }
    File confFile = new File(confDirPath, CLUE_CONF_FILE);
    if (confFile.exists() && confFile.isFile()) {
       System.out.println("using configuration file found at: " + confFile.getAbsolutePath());
       Properties props = new Properties();
       FileReader freader = new FileReader(confFile);
       props.load(freader);
       freader.close();
       return new ClueConfiguration(props);
    } else {
      // use default
      System.out.println("no configuration found, using default configuration");
      return new ClueConfiguration(new Properties());
    }
  }
  
  public static void main(String[] args) throws Exception {
    String dir = "hdfs:///Users/jwang";
    System.out.println(new URI(dir).getScheme());
  }
}
