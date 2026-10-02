package dev.by1337.particle.flow.connection;

import dev.by1337.yaml.YamlValue;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConnectionConditionTest {
    @Test
    public void parsesComparisonsAndPeriods() {
        assertTrue(ConnectionCondition.parse(" ").test(123));
        assertTrue(ConnectionCondition.parse("").test(0));
        assertTrue(ConnectionCondition.parse("<100 & % 5").test(95));
        assertFalse(ConnectionCondition.parse("<100 & % 5").test(100));
        assertFalse(ConnectionCondition.parse("<100 & % 5").test(96));
        assertTrue(ConnectionCondition.parse("<=100").test(100));
        assertFalse(ConnectionCondition.parse(">100").test(100));
        assertTrue(ConnectionCondition.parse(">=100").test(100));
        assertFalse(ConnectionCondition.parse("<100").test(100));
        assertTrue(ConnectionCondition.DECODER.decode(YamlValue.wrap("%2")).getOrThrow().test(4));
    }

    @Test
    public void rejectsMalformedConditions() {
        for (String input : new String[]{"%0", "%-2", "<", "&%2", "%2&", "<5&&%2", "=1", "<abc"}) {
            try {
                ConnectionCondition.parse(input);
                fail("Expected failure for " + input);
            } catch (IllegalArgumentException expected) {
                // Invalid expressions must not silently turn into unconditional connections.
            }
        }
    }
}
