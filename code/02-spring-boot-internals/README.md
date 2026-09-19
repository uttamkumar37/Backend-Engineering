# Code Progression — Topic 2: Spring Boot 3.x Internals

Companion code for [topics/02-spring-boot-internals.md](../../topics/02-spring-boot-internals.md).
This is a real Maven project (Spring Boot 3.3.4, Java 21, H2 for the transaction demos) — each
demo is a standalone class with its own `main`, run individually.

## Build once, then run any demo

```bash
mvn compile
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:$(cat cp.txt)"
java -cp "$CP" <fully.qualified.DemoClassName>
```

## Beginner (`beginner/`)

### BeginnerApp
The smallest useful dependency-injection example: a `GreetingService` constructor-injected with
a `MessageProvider` — proves the container resolves and wires the dependency, not `new`.

## Intermediate (`intermediate/`)

### CircularDependencyDemo
Runs the *same* circular dependency shape twice — once with field injection (resolves via the
three-level cache) and once with constructor injection (fails with a real
`BeanCurrentlyInCreationException`, printed from the actual exception, not simulated) — this is
the concept doc's Section 2 claim made concrete and observable.

### ConditionalBeanOverrideDemo
Mimics the auto-configuration override mechanism from the concept doc's Section 1:
a `@ConditionalOnMissingBean` "framework" bean backs off the moment a user `@Bean` of the same
type is registered, and a `@ConditionalOnProperty` bean is absent until the property is set.

## Advanced (`advanced/`)

### TransactionalSelfInvocationDemo
The single most requested "why isn't `@Transactional` working" bug, proven with a live check of
`TransactionSynchronizationManager.isActualTransactionActive()`: calling the annotated method via
`this` inside the same class never opens a transaction (the proxy is bypassed); calling it through
the bean fetched from the context does.

### TransactionPropagationDemo
`REQUIRES_NEW` suspending the caller's transaction: an order insert and an audit-log insert both
happen inside an outer transaction that ultimately fails and rolls back — but because the audit
write uses `REQUIRES_NEW`, it survives independently. Real H2 tables, real row counts queried
after the fact, not asserted from theory.

### RollbackRulesDemo
The default rollback rule made visible: an unchecked exception rolls back, a checked exception
does **not** roll back by default, and the same checked exception rolls back once `rollbackFor` is
set explicitly — the final row count in the ledger table shows only the "checked, default rule"
insert survives.

## How to use this progression
Run each demo and read its printed output *before* re-reading the matching section of the concept
doc — the point is to watch the framework actually do the thing the doc claims, not take it on faith.
