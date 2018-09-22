package io.dashbase.clue.util;

import java.util.Random;

public interface DocIdMatcher {
    boolean match(int docid);

    static DocIdMatcher newRandomMatcher(int percent) {

        return new DocIdMatcher() {
            Random rand = new Random();

            @Override
            public boolean match(int docid) {

                int guess = rand.nextInt(100);
                if (guess < percent) {
                    return true;
                }
                return false;
            }
        };
    }
}
