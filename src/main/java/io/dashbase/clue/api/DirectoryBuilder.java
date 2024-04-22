package io.dashbase.clue.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.IOException;
import org.apache.lucene.store.Directory;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = DefaultDirectoryBuilder.class)
@JsonSubTypes({@JsonSubTypes.Type(name = "default", value = DefaultDirectoryBuilder.class)})
public interface DirectoryBuilder {
    Directory build(String location) throws IOException;
}
