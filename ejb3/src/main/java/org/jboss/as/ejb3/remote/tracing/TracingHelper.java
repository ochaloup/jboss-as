package org.jboss.as.ejb3.remote.tracing;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.util.GlobalTracer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class TracingHelper {

    private static boolean usesDeprecatedApi = false;
    private static Constructor<?> textMapAdapterCtor;
    private static Method scopeManagerActivateMethod;

    static {
        try {
            for(Method m : ScopeManager.class.getDeclaredMethods()) {
                if(m.getName().equals("activate")) {
                    scopeManagerActivateMethod = m;
                    break;
                }
            }
            if(scopeManagerActivateMethod == null) {
                throw new IllegalStateException("Could not find ScopeManager#activate method.");
            }
            if(scopeManagerActivateMethod.getParameterCount() == 2) {
                usesDeprecatedApi = true;
            }
            if(!usesDeprecatedApi) {
                textMapAdapterCtor = ClassLoader.getSystemClassLoader()
                        .loadClass("io.opentracing.propagation.TextMapAdapter")
                        .getConstructors()[0];
            } else {
                textMapAdapterCtor = TextMapExtractAdapter.class.getConstructors()[0];
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("WildFly does not use deprecated OpenTracing API but the class " +
                    "io.opentracing.propagation.TextMapAdapter was not found on the classpath.", e);
        }
    }

    public static Scope activateSpan(Span s) {
        try {
            if(usesDeprecatedApi) {
                return (Scope) scopeManagerActivateMethod.invoke(GlobalTracer.get().scopeManager(), s, false);
            }
            return (Scope) scopeManagerActivateMethod.invoke(GlobalTracer.get().scopeManager(), s);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static TextMap textMapAdaptor(Map<String, String> stringAttachments) {
        try {
            return (TextMap) textMapAdapterCtor.newInstance(stringAttachments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
