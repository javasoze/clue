package io.dashbase.clue.api;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;

public class NewQueryParser extends QueryParser {

        public NewQueryParser(String defaultField, Analyzer analyzer) {
	    super(defaultField, analyzer);
	    setAllowLeadingWildcard(true);
	}
}
