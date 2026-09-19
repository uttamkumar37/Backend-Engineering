package com.backendplan.microservices.advanced;

import java.util.ArrayList;
import java.util.List;

// A minimal saga orchestrator: each step has a matching compensating action. When a step fails,
// every already-completed step's compensation runs in reverse order - no distributed transaction,
// no locks held across services, just an explicit, observable state machine.
public class SagaOrchestrationDemo {

    sealed interface SagaResult permits SagaResult.Success, SagaResult.Compensated {
        record Success(String orderId) implements SagaResult {}
        record Compensated(String orderId, String failedStep, String reason) implements SagaResult {}
    }

    record SagaStep(String name, Runnable action, Runnable compensation) {}

    static SagaResult runSaga(String orderId, List<SagaStep> steps, String stepToFail) {
        List<SagaStep> completed = new ArrayList<>();
        for (SagaStep step : steps) {
            try {
                if (step.name().equals(stepToFail)) {
                    throw new RuntimeException("simulated failure in step: " + step.name());
                }
                step.action().run();
                completed.add(step);
            } catch (Exception e) {
                System.out.println("Step FAILED: " + step.name() + " - " + e.getMessage());
                System.out.println("Running compensations in reverse order:");
                for (int i = completed.size() - 1; i >= 0; i--) {
                    completed.get(i).compensation().run();
                }
                return new SagaResult.Compensated(orderId, step.name(), e.getMessage());
            }
        }
        return new SagaResult.Success(orderId);
    }

    public static void main(String[] args) {
        List<SagaStep> orderSaga = List.of(
                new SagaStep("ReserveInventory",
                        () -> System.out.println("  [action] inventory reserved"),
                        () -> System.out.println("  [compensate] inventory reservation released")),
                new SagaStep("ChargePayment",
                        () -> System.out.println("  [action] payment charged"),
                        () -> System.out.println("  [compensate] payment refunded")),
                new SagaStep("ScheduleShipment",
                        () -> System.out.println("  [action] shipment scheduled"),
                        () -> System.out.println("  [compensate] shipment cancelled"))
        );

        System.out.println("=== Happy path: all steps succeed ===");
        SagaResult result1 = runSaga("ord-1", orderSaga, null);
        System.out.println("Result: " + result1);

        System.out.println();
        System.out.println("=== ScheduleShipment fails: ReserveInventory and ChargePayment compensate ===");
        SagaResult result2 = runSaga("ord-2", orderSaga, "ScheduleShipment");
        System.out.println("Result: " + result2);

        switch (result2) {
            case SagaResult.Success s -> System.out.println("Order " + s.orderId() + " completed cleanly.");
            case SagaResult.Compensated c -> System.out.println(
                    "Order " + c.orderId() + " was rolled back at step '" + c.failedStep()
                            + "' - the compiler forces us to handle this case explicitly.");
        }
    }
}
