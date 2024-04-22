package io.dashbase.clue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.dashbase.clue.api.*;
import io.dashbase.clue.commands.CommandRegistrar;
import io.dashbase.clue.commands.DefaultCommandRegistrar;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Groups the configuration options for the application.
 * <p>
 * The settings can be overridden through the Yaml configuration file. See 'test_config.yml'
 * for an example.
 */
public class ClueAppConfiguration {
    public QueryBuilder queryBuilder = new DefaultQueryBuilder();
    public DirectoryBuilder dirBuilder = new DefaultDirectoryBuilder();
    public IndexReaderFactory indexReaderFactory = new DefaultIndexReaderFactory();
    public AnalyzerFactory analyzerFactory = new DefaultAnalyzerFactory();
    public CommandRegistrar commandRegistrar = new DefaultCommandRegistrar();

    private static final String CLUE_CONF_FILE = "clue.yml";
    static final ObjectMapper MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        mapper.registerModule(new ParameterNamesModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        return mapper;
    }

    public static ClueAppConfiguration load() throws IOException {
        String confDirPath = System.getProperty("config.dir");
        if (confDirPath == null) {
            confDirPath = "config";
        }
        File confFile = new File(confDirPath, CLUE_CONF_FILE);
        if (confFile.exists() && confFile.isFile()) {

            System.out.println("using configuration file found at: " + confFile.getAbsolutePath());
            return load(confFile);
        } else {
            // use default
            System.out.println("no configuration found, using default configuration");
            return new ClueAppConfiguration();
        }
    }

    static ClueAppConfiguration load(File confFile) throws IOException {
        try (FileReader reader = new FileReader(confFile)) {
            return MAPPER.readValue(reader, ClueAppConfiguration.class);
        }
    }
}
