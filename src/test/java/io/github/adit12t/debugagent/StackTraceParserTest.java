package io.github.adit12t.debugagent;

import io.github.adit12t.debugagent.Model.ParsedTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackTraceParserTest {

    private final StackTraceParser parser = new StackTraceParser();

    @Test
    void parsesTypeMessageAndFrames() {
        ParsedTrace trace = parser.parse("""
                java.lang.NullPointerException: boom
                \tat demo.orders.DiscountService.applyCoupon(DiscountService.java:17)
                \tat java.base/java.lang.Thread.run(Thread.java:1583)
                """);

        assertEquals("java.lang.NullPointerException", trace.exceptionType());
        assertEquals("boom", trace.exceptionMessage());
        assertEquals(2, trace.frames().size());
        assertEquals("demo.orders.DiscountService", trace.frames().get(0).className());
        assertEquals(17, trace.frames().get(0).line());
        assertEquals("java.lang.Thread", trace.frames().get(1).className());
    }

    @Test
    void usesTheLastCausedByAsTheRootException() {
        ParsedTrace trace = parser.parse("""
                java.lang.IllegalStateException: wrapper
                \tat demo.orders.web.SummaryController.summary(SummaryController.java:58)
                Caused by: java.lang.ArithmeticException: / by zero
                \tat demo.orders.OrderService.averageItemPriceCents(OrderService.java:25)
                \t... 1 more
                """);

        assertEquals("java.lang.ArithmeticException", trace.exceptionType());
        assertEquals("/ by zero", trace.exceptionMessage());
        assertEquals(1, trace.frames().size());
        assertEquals("averageItemPriceCents", trace.frames().get(0).method());
    }
}
