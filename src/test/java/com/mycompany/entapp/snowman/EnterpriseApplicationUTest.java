package com.mycompany.entapp.snowman;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class EnterpriseApplicationUTest {

    @After
    public void clearPort() {
        System.clearProperty("port");
    }

    @Test
    public void usesConfiguredPort() throws Exception {
        System.setProperty("port", "8181");

        assertEquals(8181, invokeResolvePort());
    }

    @Test
    public void usesDefaultPortWhenConfigurationIsInvalid() throws Exception {
        System.setProperty("port", "invalid");

        assertEquals(8090, invokeResolvePort());
    }

    @Test
    public void resolvesPackagedWebDescriptor() throws Exception {
        Method method = EnterpriseApplication.class.getDeclaredMethod("getResource", String.class);
        method.setAccessible(true);

        String resource = (String) method.invoke(null, "webapp/WEB-INF/web.xml");

        org.junit.Assert.assertTrue(resource.endsWith("webapp/WEB-INF/web.xml"));
    }

    @Test(expected = RuntimeException.class)
    public void rejectsMissingWebResource() throws Throwable {
        Method method = EnterpriseApplication.class.getDeclaredMethod("getResource", String.class);
        method.setAccessible(true);
        try {
            method.invoke(null, "missing-resource.xml");
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private int invokeResolvePort() throws Exception {
        Method method = EnterpriseApplication.class.getDeclaredMethod("resolvePort");
        method.setAccessible(true);
        return (Integer) method.invoke(null);
    }
}
