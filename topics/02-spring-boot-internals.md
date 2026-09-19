# Topic 2 — Spring Boot 3.x Internals

Assumes you already use `@Autowired`, `@Transactional`, `@Configuration`. This is about what the
container actually does with those annotations, where the proxy-based model breaks down, and the
failure modes senior engineers are expected to diagnose without a debugger.

---

## 1. Auto-configuration — the actual mechanism

- **Discovery has changed since Boot 2.7**: auto-configuration classes are no longer listed under
  `META-INF/spring.factories` (`EnableAutoConfiguration` key) — they're listed one-per-line in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. `spring.factories`
  still exists for other extension points (e.g., `ApplicationListener`, `AutoConfigurationImportFilter`),
  which is a common point of confusion when a library still ships the old format for auto-config
  and silently doesn't activate on Boot 3.
- **Condition evaluation is a two-pass, ordered process.** Each auto-configuration class is
  wrapped in `@Conditional` variants — `@ConditionalOnClass` (classpath check via ASM, without
  loading the class — this is why a missing optional dependency doesn't throw `ClassNotFoundException`
  at this stage), `@ConditionalOnMissingBean`, `@ConditionalOnProperty`, `@ConditionalOnBean`.
  `@ConditionalOnMissingBean`/`@ConditionalOnBean` conditions are **deferred** and evaluated in
  auto-configuration processing order (`@AutoConfigureOrder`, `@AutoConfigureBefore/After`)
  because they depend on what other configurations have already registered — get the ordering
  wrong when writing your own auto-configuration and `@ConditionalOnMissingBean` silently doesn't
  see a bean that's defined "after" it in the resolved order.
  - **Debug this with `--debug` or `management.endpoint.conditions` / the `/actuator/conditions`
    endpoint** — it prints the `ConditionEvaluationReport`: which auto-configs were applied and
    which were excluded and why. This is the single most useful tool for "why isn't my
    auto-configuration kicking in" bugs, and most engineers don't know it exists.
- **Your own `@Configuration` always wins.** User-defined beans are processed before
  auto-configuration (auto-config classes are `@Order(Ordered.LOWEST_PRECEDENCE)`-ish, loaded
  last), which is why `@ConditionalOnMissingBean` on a framework-provided bean lets you override
  it just by declaring your own `@Bean` of the same type — no exclusion annotation needed. Use
  `@EnableAutoConfiguration(exclude = ...)` only when you need to suppress a whole auto-config
  class, not just override one bean it defines.
- **Property binding happens via relaxed binding + `@ConfigurationProperties`,** not simple
  string lookup — `my-service.max-retries`, `my-service.maxRetries`, and `MY_SERVICE_MAX_RETRIES`
  (env var) all bind to the same field, resolved through `Binder`/`PropertySource` precedence
  (command-line args > env vars > `application-{profile}.yml` > `application.yml` > defaults).
  Getting this precedence wrong in production ("why did the env var not override my yaml?") is a
  frequent on-call issue — it's almost always a profile-specific file loaded after the env var in
  the resolution order was expected to win.

---

## 2. Bean lifecycle — the sequence that actually runs

1. **`BeanDefinition` registration** (from component scan, `@Bean` methods, or XML) — no instances
   exist yet, just metadata (class, scope, dependencies).
2. **`BeanFactoryPostProcessor`** runs against the *definitions* (e.g., `PropertySourcesPlaceholderConfigurer`
   resolving `${...}` in definitions) — this is definition-time, not instance-time.
3. For each bean, in dependency order: **instantiation** → **`InstantiationAwareBeanPostProcessor`**
   (property population, e.g. `@Autowired` field/setter injection happens here via
   `AutowiredAnnotationBeanPostProcessor`) → **`Aware` interfaces** (`BeanNameAware`,
   `ApplicationContextAware`, etc.) → **`BeanPostProcessor.postProcessBeforeInitialization`** →
   **`@PostConstruct` / `InitializingBean.afterPropertiesSet()` / custom `init-method`** →
   **`BeanPostProcessor.postProcessAfterInitialization`** (this is where AOP proxies get created —
   see below) → bean is ready.
4. On shutdown: **`@PreDestroy` / `DisposableBean.destroy()`**, in reverse dependency order.

**Circular dependency resolution** (setter/field injection only): Spring uses a **three-level
cache** — `singletonObjects` (fully initialized), `earlySingletonObjects` (raw instance, not yet
proxied/populated), `singletonFactories` (a factory that can produce the early reference, applying
any proxying). When bean A needs bean B which needs bean A, Spring hands B a reference to A's
*early, unfinished* instance from the singleton factory, finishes B, then finishes A. **This is
also why constructor injection cannot resolve circular dependencies** — a constructor needs a
*complete* argument, and there's no "early reference" you can hand to a constructor before the
object exists at all; you get `BeanCurrentlyInCreationException`. The practical fix is always to
break the cycle (extract a shared collaborator, use `@Lazy` on one side, or redesign the
dependency) — `@Lazy` on a constructor param works by injecting a lazy-init proxy, not the real
early reference, so it's a real fix, not a workaround for a container limitation.

---

## 3. AOP — how the proxy actually gets in the way

- **Spring AOP is proxy-based by default**, not compile-time/load-time weaving (that's full
  AspectJ, rarely used in Spring Boot apps). A `BeanPostProcessor`
  (`AnnotationAwareAspectJAutoProxyCreator`) wraps the target bean in a proxy during
  `postProcessAfterInitialization` if it matches any pointcut (`@Transactional`, `@Async`,
  `@Cacheable`, or a custom `@Aspect`).
- **Two proxy strategies**: JDK dynamic proxies (implements the same interfaces as the target,
  requires at least one interface) or CGLIB/ByteBuddy subclass proxies (extends the concrete
  class — this is why the target class and its intercepted methods **cannot be `final`**, a CGLIB
  proxy can't override a final method, and Spring silently produces a proxy that doesn't intercept
  it, which is a much worse failure mode than a clear error). Spring Boot defaults
  `proxyTargetClass=true` (CGLIB-style) for consistency, even when interfaces exist.
- **Self-invocation bypasses the proxy — the single most common AOP bug report.** If method `a()`
  calls `this.b()` within the same class, and `b()` is `@Transactional`/`@Async`/`@Cacheable`,
  the call goes directly to the real object, never through the proxy, so the annotation is
  silently ignored. Fixes: inject a self-reference proxy (`@Autowired private Self self;` pointing
  at the bean itself, or `AopContext.currentProxy()` with exposeProxy enabled), or — the better
  fix — move `b()` into a separate collaborator bean and call through that.
- **Multiple aspects/advices ordering**: controlled via `@Order` on the aspect class (lower value
  = outer, runs first on the way in, last on the way out — like nested try/finally). Getting this
  wrong causes subtle bugs like a `@Transactional` boundary closing *before* a logging/metrics
  aspect that assumed it was still inside the transaction.

---

## 4. Transactions — `@Transactional` under the hood

- **Implementation**: `@Transactional` is just another AOP advice
  (`TransactionInterceptor` wrapping the target via the same proxy mechanism above) — so
  everything above about self-invocation and final methods applies directly to transactions, and
  is the #1 reason "`@Transactional` isn't working" bugs happen.
- **Propagation is about how the proxy's interceptor treats an already-active transaction**,
  tracked via `TransactionSynchronizationManager`, a set of `ThreadLocal`s binding the current
  `Connection`/session to the executing thread:
  - `REQUIRED` (default): join the caller's transaction if one exists, else start one.
  - `REQUIRES_NEW`: suspend the caller's transaction (detach its resources from the thread-local),
    start a fully independent one, commit/rollback it, then resume the original. Use for "always
    log this audit record even if the outer transaction rolls back."
  - `NESTED`: uses a JDBC savepoint within the *same* physical transaction/connection — a rollback
    in the nested scope rolls back to the savepoint, not the whole transaction. Not supported by
    all drivers/transaction managers (works with `DataSourceTransactionManager`, not with JTA in
    general).
  - `MANDATORY`/`NEVER`/`NOT_SUPPORTED`/`SUPPORTS`: mostly used to enforce or document the calling
    contract explicitly rather than change runtime resource behavior in most single-datasource apps.
- **Default rollback rule**: rolls back on unchecked exceptions (`RuntimeException`, `Error`), does
  **not** roll back on checked exceptions unless you specify `rollbackFor = MyCheckedException.class`.
  This trips people up when wrapping a checked `IOException`-throwing call and expecting an
  automatic rollback — it silently commits partial work unless you've been explicit.
  - Catching the exception *inside* the `@Transactional` method and not rethrowing also prevents
    rollback (the interceptor only sees what propagates out) — a common accidental-swallow bug in
    try/catch-and-log code paths inside transactional methods.
- **`readOnly = true` is a hint, not a guarantee**: it lets Hibernate skip dirty-checking flushes
  and can let some JDBC drivers/DB proxies route to a read replica, but it does **not** prevent
  writes at the JVM level — an `INSERT` inside a `readOnly` transactional method may or may not
  fail depending on the driver, so don't rely on it as an authorization control.
- **Transaction boundary width is a real production concern**: a `@Transactional` service method
  that calls out to an external HTTP API or publishes to Kafka *inside* the transaction holds a
  DB connection (from the pool) for the entire external call's duration. Under load, this
  exhausts the connection pool long before the DB itself is the bottleneck. The fix is almost
  always to narrow the transaction (do the DB work, commit, *then* call out) and handle
  cross-system consistency with the **outbox pattern** (Topic 4), not a wide transaction.
- **Read-only transactional methods still trigger the N+1 problem** — `readOnly` affects flushing,
  not fetch strategy. Lazy-loaded associations accessed inside the method still issue one query
  per association per row unless the fetch plan (`JOIN FETCH`, entity graph, or a projection) is
  fixed at the query level (Topic 3 covers this in depth).

---

## Interview-depth Q&A

1. A library's auto-configuration doesn't activate on Boot 3 even though it worked on Boot 2. What's
   the first thing you check, and why?
2. Explain why `@ConditionalOnMissingBean` can behave differently depending on auto-configuration
   ordering, and how you'd debug an ordering-related failure without reading source code line by line.
3. A colleague's fix for a broken `@Transactional` self-invocation call was to mark the method
   `public` (it was `private`). Why doesn't that fix it, and what's the actual fix?
4. Why does `REQUIRES_NEW` inside a loop calling an external audit-log save risk connection pool
   exhaustion under load, even though each transaction is short?
5. A service marks a method `@Transactional(readOnly = true)` and still sees N+1 queries in the
   logs. Where do you look first?
6. Walk through what happens, step by step, when bean A (constructor-injected) and bean B
   (setter-injected) have a circular dependency — why does one combination work and the other throw?

## Proof of learning
_Write one paragraph on a bug or design decision from your own project history that this topic
would have explained faster — self-invocation, connection pool exhaustion, or a circular
dependency you hit and worked around without knowing why it happened._
