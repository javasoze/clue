package io.dashbase.clue.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = DefaultDirectoryBuilder.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "default", value = DefaultDirectoryBuilder.class)
})
public interface DirectoryBuilder {
  Directory build(String location) throws IOException;
}
