package com.senseidb.clue.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.json.JSONObject;

public class BuildSampleIndex {

  static void addMetaString(Document doc, String field, String value) {
    if (value != null) {
      doc.add(new SortedDocValuesField(field, new BytesRef(value)));
      doc.add(new StringField(field+"_indexed", value, Store.NO));
    }
  }
  
  static Document buildDoc(JSONObject json) throws Exception{
    Document doc = new Document();
    
    doc.add(new NumericDocValuesField("id", json.getLong("id")));
    doc.add(new DoubleDocValuesField("price", json.optDouble("price")));
    doc.add(new TextField("contents", json.optString("contents"), Store.NO));
    doc.add(new NumericDocValuesField("year", json.optInt("year")));
    doc.add(new NumericDocValuesField("mileage", json.optInt("mileage")));
    
    addMetaString(doc,"color", json.optString("color"));
    addMetaString(doc,"category", json.optString("category"));
    addMetaString(doc,"makemodel", json.optString("makemodel"));
    addMetaString(doc,"city", json.optString("city"));
    
    String tagsString = json.optString("tags");
    if (tagsString != null) {
      String[] parts = tagsString.split(",");
      if (parts != null && parts.length > 0) {
        for (String part : parts) {
          doc.add(new SortedSetDocValuesField("tags", new BytesRef(part)));
          doc.add(new StringField("tags_indexed", part, Store.NO));
        }
      }
    }
    
    doc.add(new BinaryDocValuesField("json", new BytesRef(json.toString())));
    return doc;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception{
    if (args.length != 2) {
      System.out.println("usage: source_file index_dir");
    }
    File f = new File(args[0]);
    BufferedReader reader = new BufferedReader(new FileReader(f));
    
    File idxDir = new File(args[1]);
    
    IndexWriterConfig idxWriterConfig = new IndexWriterConfig(Version.LUCENE_43, new StandardAnalyzer(Version.LUCENE_43));
    Directory dir = FSDirectory.open(idxDir);
    IndexWriter writer = new IndexWriter(dir, idxWriterConfig);
    int count = 0;
    while (true) {
      String line = reader.readLine();
      if (line == null) break;
      
      JSONObject json = new JSONObject(line);
      Document doc = buildDoc(json);
      writer.addDocument(doc);
      count++;
      if (count % 100 == 0) {
        System.out.print(".");
      }
    }
    
    System.out.println(count+" docs indexed");
    
    reader.close();
    writer.commit();
    writer.close();
  }

}
