package io.dashbase.clue;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dashbase.clue.api.*;
import io.dashbase.clue.commands.CommandRegistrar;
import io.dashbase.clue.commands.DefaultCommandRegistrar;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ClueAppConfiguration  {
    public QueryBuilder queryBuilder = new DefaultQueryBuilder();
    public DirectoryBuilder dirBuilder = new DefaultDirectoryBuilder();
    public IndexReaderFactory indexReaderFactory = new DefaultIndexReaderFactory();
    public AnalyzerFactory analyzerFactory = new DefaultAnalyzerFactory();
    public CommandRegistrar commandRegistrar = new DefaultCommandRegistrar();
    public boolean enableConcurrency = false;

    private static final String CLUE_CONF_FILE = "clue.yml";
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public static ClueAppConfiguration load() throws IOException {
        String confDirPath = System.getProperty("config.dir");
        if (confDirPath == null) {
            confDirPath = "config";
        }
        File confFile = new File(confDirPath, CLUE_CONF_FILE);
        if (confFile.exists() && confFile.isFile()) {

            System.out.println("using configuration file found at: " + confFile.getAbsolutePath());
            try (FileReader freader = new FileReader(confFile)) {
                return MAPPER.readValue(freader, ClueAppConfiguration.class);
            }
        } else {
            // use default
            System.out.println("no configuration found, using default configuration");
            return new ClueAppConfiguration();
        }
    }
}
