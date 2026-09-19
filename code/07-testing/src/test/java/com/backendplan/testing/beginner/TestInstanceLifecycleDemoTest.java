package com.backendplan.testing.beginner;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

// PER_METHOD (default): a fresh instance per @Test method - instance fields never leak
// between tests, so forgetting to reset one is usually harmless.
// PER_CLASS: one instance shared across all @Test methods - convenient for non-static
// @BeforeAll, but instance fields now silently accumulate state across tests.
public class TestInstanceLifecycleDemoTest {

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PerMethodIsIsolated {
        int counter = 0;

        @Test
        @Order(1)
        void firstTestIncrements() {
            counter++;
            assertEquals(1, counter, "fresh instance - starts at 0, becomes 1");
        }

        @Test
        @Order(2)
        void secondTestSeesAFreshInstance() {
            assertEquals(0, counter, "PER_METHOD: this is a NEW instance, unaffected by the previous test");
            counter++;
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PerClassLeaksState {
        int counter = 0;

        @Test
        @Order(1)
        void firstTestIncrements() {
            counter++;
            assertEquals(1, counter);
        }

        @Test
        @Order(2)
        void secondTestSeesLeakedState() {
            assertEquals(1, counter, "PER_CLASS: same instance as the previous test - state leaked in");
            counter++;
        }
    }
}
