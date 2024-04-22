package io.dashbase.clue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.io.Resources;
import java.io.File;
import org.junit.jupiter.api.Test;

class ClueAppConfigurationTest {

    @Test
    public void testLoadConfiguration() throws Exception {
        File configFile =
                new File(Resources.getResource("io/dashbase/clue/test_config.yml").toURI());

        ClueAppConfiguration config = ClueAppConfiguration.load(configFile);
        assertNotNull(config);
        assertNotNull(config.analyzerFactory.forQuery());
        assertNotNull(config.analyzerFactory.forIndexing());
    }
}
