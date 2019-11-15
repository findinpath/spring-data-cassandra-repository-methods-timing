package com.findinpath.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.aop.framework.Advised;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * AspectJ aspect for intercepting the spring data repositories on any of the following criteria:
 *
 * <ul>
 *   <li>the class is annotated with the @org.springframework.stereotype.Repository annotation</li>
 *   <li>the class implements org.springframework.data.repository.Repository class </li>
 * </ul>
 *
 *
 * The duration of the method calls on the repository classes will be
 * published towards Micrometer's {@link MeterRegistry} as {@link Timer} information.
 *
 * There is made a distinction when emitting the metrics whether the call was successful
 * or not (through the &quot;successful&quot; metric tag).
 */
@Aspect
@Component
public class RepositoryTimerAspect {

  public static final double[] EXPORTED_PERCENTILES = {0.5, 0.75, 0.9, 0.95, 0.99};

  public static final String REPOSITORY_METRIC_NAME = "repository";

  private final MeterRegistry meterRegistry;

  public RepositoryTimerAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  private static void emitMetrics(MeterRegistry meterRegistry,
      Timer.Sample sample, String className, String methodName, Optional<Throwable> exception) {
    String successful = exception.isPresent() ? "false" : "true";
    sample.stop(Timer
        .builder(REPOSITORY_METRIC_NAME)
        .tags("class", className)
        .tags("method", methodName)
        .tags("successful", successful)
        .publishPercentiles(EXPORTED_PERCENTILES)
        .register(meterRegistry));
  }

  private static String getDeclaredClassName(ProceedingJoinPoint proceedingJoinPoint) {
    String typeName = "Repository";
    if (proceedingJoinPoint instanceof MethodInvocationProceedingJoinPoint) {
      var proxy = proceedingJoinPoint.getThis();
      if (proxy instanceof Advised) {
        Class<?>[] proxiedInterfaces = ((Advised) proxy).getProxiedInterfaces();
        if (proxiedInterfaces != null && proxiedInterfaces.length > 1) {
          // Through the Advised object, we can get the proxied
          // interfaces using {@link Advised#getProxiedInterfaces()}
          // In this case, the type that we look for is the first one in the list of interfaces.
          typeName = proxiedInterfaces[0].getSimpleName();
        } else {
          typeName = proceedingJoinPoint.getTarget().getClass().getSimpleName();
        }
      }
    }

    return typeName;
  }

  @Pointcut("execution(public * org.springframework.data.repository.Repository+.*(..)) ||  within(@org.springframework.stereotype.Repository *)")
  public void repositoryClassMethods() {
  }

  @Around("repositoryClassMethods()")
  public Object measureMethodExecutionTime(ProceedingJoinPoint proceedingJoinPoint)
      throws Throwable {
    var methodName = proceedingJoinPoint.getSignature().getName();
    if ("toString".equals(methodName)) {
      return proceedingJoinPoint.proceed();
    }

    Signature signature = proceedingJoinPoint.getSignature();
    Class returnType = ((MethodSignature) signature).getReturnType();

    if (ListenableFuture.class.equals(returnType)) {
      return measureAsyncMethodExecutionTime(proceedingJoinPoint);
    } else {
      return measureSyncMethodExecutionTime(proceedingJoinPoint);
    }
  }

  private Object measureAsyncMethodExecutionTime(ProceedingJoinPoint proceedingJoinPoint)
      throws Throwable {
    // We'll need to measure the execution time of the asynchronous method,
    // in a callback after its completion.
    var sample = Timer.start();
    var className = getDeclaredClassName(proceedingJoinPoint);
    var methodName = proceedingJoinPoint.getSignature().getName();

    var asyncResult = (ListenableFuture) proceedingJoinPoint.proceed();
    asyncResult.addCallback(
        result -> emitMetrics(meterRegistry, sample, className, methodName, Optional.empty()),
        ex -> emitMetrics(meterRegistry, sample, className, methodName, Optional.of(ex)));
    return asyncResult;

  }

  private Object measureSyncMethodExecutionTime(ProceedingJoinPoint proceedingJoinPoint)
      throws Throwable {
    Optional<Throwable> throwable = Optional.empty();
    var sample = Timer.start();
    var className = getDeclaredClassName(proceedingJoinPoint);
    var methodName = proceedingJoinPoint.getSignature().getName();

    try {
      return proceedingJoinPoint.proceed();
    } catch (Throwable ex) {
      throwable = Optional.of(ex);
      throw ex;
    } finally {
      emitMetrics(meterRegistry, sample, className, methodName, throwable);
    }
  }
}
