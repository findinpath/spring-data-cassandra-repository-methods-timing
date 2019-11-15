package com.findinpath.config;

import com.findinpath.aop.RepositoryTimerAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.context.annotation.Bean;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * This configuration class acts like a alternative/backup in case that the AspectJ implementation
 * for timing the repositories doesn't fit for the project where the learning from this demo could
 * be integrated to.
 *
 * Through Spring AOPs are advised methods of spring data repository beans, which are any of
 * the following:
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
 *
 * <b>NOTE</b> that in case that you want to test the project
 * with this feature, the commented annotations below should be uncommented and also the
 * `RepositoryTimerAspect` should be deleted from the source code.
 *
 * @see RepositoryTimerAspect
 * @deprecated The {@link RepositoryTimerAspect} is responsible for the functionality of timing the
 * spring data repository methods
 */
//@ImportResource("classpath:/repository-timer-aop-config.xml")
//@Configuration
public class RepositoryTimerConfiguration {

  public static final double[] EXPORTED_PERCENTILES = {0.5, 0.75, 0.9, 0.95, 0.99};

  public static final String REPOSITORY_METRIC_NAME = "repository";

  private static String getDeclaredClassName(MethodInvocation invocation) {
    String typeName = "Repository";
    if (invocation instanceof ReflectiveMethodInvocation) {
      Object proxy = ((ReflectiveMethodInvocation) invocation).getProxy();
      if (proxy instanceof Advised) {
        Class<?>[] proxiedInterfaces = ((Advised) proxy).getProxiedInterfaces();
        if (proxiedInterfaces != null && proxiedInterfaces.length > 1) {
          // Through the Advised object, we can get the proxied
          // interfaces using {@link Advised#getProxiedInterfaces()}
          // In this case, the type that we look for is the first one in the list of interfaces.
          typeName = proxiedInterfaces[0].getSimpleName();
        } else {
          typeName = invocation.getThis().getClass().getSimpleName();
        }
      }
    }

    return typeName;
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

  @Bean
  public MethodInterceptor repositoryTimerMethodInterceptor(final MeterRegistry meterRegistry) {

    return invocation -> {
      if ("toString".equals(invocation.getMethod().getName())) {
        // no metrics needed in this case.
        return invocation.proceed();
      }

      return timeMethodInvocation(meterRegistry, invocation);
    };
  }

  private Object timeMethodInvocation(MeterRegistry meterRegistry,
      MethodInvocation invocation) throws Throwable {
    if (ListenableFuture.class.equals(invocation.getMethod().getReturnType())) {
      return timeAsynchronousMethodInvocation(meterRegistry, invocation);
    } else {
      return timeSynchronousMethodInvocation(meterRegistry, invocation);
    }
  }

  private Object timeAsynchronousMethodInvocation(MeterRegistry meterRegistry,
      MethodInvocation invocation)
      throws Throwable {
    // We'll need to measure the execution time of the asynchronous method,
    // in a callback after its completion.
    var sample = Timer.start();
    var className = getDeclaredClassName(invocation);
    var methodName = invocation.getMethod().getName();

    var asyncResult = (ListenableFuture) invocation.proceed();
    asyncResult.addCallback(
        result -> emitMetrics(meterRegistry, sample, className, methodName, Optional.empty()),
        ex -> emitMetrics(meterRegistry, sample, className, methodName, Optional.of(ex)));
    return asyncResult;

  }

  private Object timeSynchronousMethodInvocation(final MeterRegistry meterRegistry,
      final MethodInvocation invocation) throws Throwable {
    Optional<Throwable> throwable = Optional.empty();
    var sample = Timer.start();
    var className = getDeclaredClassName(invocation);
    var methodName = invocation.getMethod().getName();
    try {
      return invocation.proceed();
    } catch (Throwable ex) {
      throwable = Optional.of(ex);
      throw ex;
    } finally {
      emitMetrics(meterRegistry, sample, className, methodName, throwable);
    }
  }
}
