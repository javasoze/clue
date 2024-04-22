package io.dashbase.clue.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = DefaultIndexReaderFactory.class)
@JsonSubTypes({@JsonSubTypes.Type(name = "default", value = DefaultIndexReaderFactory.class)})
public interface IndexReaderFactory {
    void initialize(Directory dir) throws Exception;

    IndexReader getIndexReader();

    void refreshReader() throws Exception;

    void shutdown() throws Exception;
}
