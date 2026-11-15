package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.Finding;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the whole pipeline on the sample data shipped with the repository. */
class DebugAgentTest {

    @Test
    void findsBothBugsAndVerifiesThemAgainstPostFixLogs() throws IOException {
        List<Finding> findings = DebugAgent.run(
                Path.of("sample-data/logs-before.ndjson"),
                Path.of("sample-data/logs-after.ndjson"),
                Path.of("sample-app/src/main/java"),
                "demo.orders",
                new RuleBasedAnalyst());

        assertEquals(2, findings.size());

        Finding nullPointer = findings.get(0);
        assertEquals("NullPointerException", nullPointer.group().trace().simpleType());
        assertEquals(3, nullPointer.group().count());
        assertEquals("applyCoupon", nullPointer.group().appFrame().method());
        assertTrue(nullPointer.snippet().failingLine().contains("coupon.percentOff()"));
        assertTrue(nullPointer.diagnosis().rootCause().contains("`coupon` is null"));
        assertTrue(nullPointer.diagnosis().regressionTest().contains("applyCoupon_whenCouponIsNull"));
        assertTrue(nullPointer.verification().resolved());

        Finding divideByZero = findings.get(1);
        assertEquals("ArithmeticException", divideByZero.group().trace().simpleType());
        assertEquals(2, divideByZero.group().count());
        assertTrue(divideByZero.snippet().failingLine().contains("itemPricesCents.size()"));
        assertFalse(divideByZero.verification().resolved());
        assertEquals(1, divideByZero.verification().occurrencesAfter());
    }
}
