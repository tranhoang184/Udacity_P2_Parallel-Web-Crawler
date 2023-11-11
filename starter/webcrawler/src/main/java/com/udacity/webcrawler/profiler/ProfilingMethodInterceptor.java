package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.lang.Object;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
    private final Object target;
    private final ProfilingState state;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
    ProfilingMethodInterceptor(Clock clock, Object target, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.target = target;
    this.state = state;
  }

  @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
        Instant start = clock.instant();
        Object rlt;
        try {
//            rlt = method.invoke(target, args);
            if(method.getName().equals("equals") && method.getDeclaringClass().getName().equals("java.lang.Object")){
                rlt = target.equals(args[0]);
            } else{
                rlt = method.invoke(target, args);
            }
        } catch (InvocationTargetException et) {
            throw et.getTargetException();
        } catch (IllegalAccessException ea) {
            throw new RuntimeException(ea);
        } finally {
			if (method.isAnnotationPresent(Profiled.class)) {
			    Duration duration = Duration.between(start, clock.instant());
			    state.record(target.getClass(), method, duration);
            }
        }
        return rlt;
  }
}
