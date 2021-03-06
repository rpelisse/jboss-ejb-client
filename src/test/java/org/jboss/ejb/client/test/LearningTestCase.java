package org.jboss.ejb.client.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.ClusterAffinity;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.test.common.DummyServer;
import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.ejb.client.test.common.Foo;
import org.jboss.ejb.client.test.common.FooBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.naming.client.WildFlyInitialContext;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * @author Jason T. Greene
 */
public class LearningTestCase {
    private static final Logger logger = Logger.getLogger(LearningTestCase.class);

    // module
    private static final String APP_NAME = "my-foo-app";
    private static final String MODULE_NAME = "my-bar-module";
    private static final String DISTINCT_NAME = "";

    private static final String SERVER_NAME = "test-server";


    private static DummyServer server;
    private static  boolean serverStarted = false;

    @BeforeClass
    public static void beforeTest() throws Exception {
        // start a server
        server = new DummyServer("localhost", 6999, SERVER_NAME);
        server.start();
        serverStarted = true;
        logger.info("Started server ...");

        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean());
        server.register(APP_NAME, MODULE_NAME, DISTINCT_NAME, FooBean.class.getSimpleName(), new FooBean());
        logger.info("Registered module ...");
    }

    @AfterClass
    public static void afterTest() {
        server.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getName());
        server.unregister(APP_NAME, MODULE_NAME, DISTINCT_NAME, FooBean.class.getName());
        logger.info("Unregistered module ...");

        if (serverStarted) {
            try {
                server.stop();
            } catch (Throwable t) {
                logger.info("Could not stop server", t);
            }
        }
        logger.info("Stopped server ...");
    }

    private void verifyAffinity(FastHashtable<String, Object> props, Affinity match1, Affinity match2) throws NamingException {
        WildFlyRootContext context = new WildFlyRootContext(props);

        Object echo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!" + Echo.class.getName() + "?stateful");

        Assert.assertEquals(match1, EJBClient.getStrongAffinity(echo));

        EJBClient.setStrongAffinity(echo, new ClusterAffinity("test"));
        ((Echo)echo).echo("test");

        Object foo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + FooBean.class.getSimpleName() + "!" + Foo.class.getName() + "?stateful");

        Assert.assertEquals(match2, EJBClient.getStrongAffinity(foo));
    }

    private void verifyAffinity(WildFlyRootContext context, Affinity match1, Affinity match2) throws NamingException {
          Object echo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!" + Echo.class.getName() + "?stateful");

          Assert.assertEquals(match1, EJBClient.getStrongAffinity(echo));

          EJBClient.setStrongAffinity(echo, new ClusterAffinity("test"));
          ((Echo)echo).echo("test");

          Object foo = context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + FooBean.class.getSimpleName() + "!" + Foo.class.getName() + "?stateful");

          Assert.assertEquals(match2, EJBClient.getStrongAffinity(foo));
      }


    @Test
     public void testLearning() throws Exception {
         FastHashtable<String, Object> props = new FastHashtable<>();
         props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
         props.put("java.naming.provider.url", "remote://localhost:6999");

         verifyAffinity(props, Affinity.NONE, new ClusterAffinity("test"));
     }

    @Test
    public void testDisabledLearning() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");
        props.put("jboss.disable-affinity-learning", "true");

        verifyAffinity(props, Affinity.NONE, Affinity.NONE);
    }

    @Test
    public void testExplicitlyDefined() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");
        props.put("jboss.cluster-affinity", "bob");

        ClusterAffinity bob = new ClusterAffinity("bob");
        verifyAffinity(props, bob, bob);
    }

    @Test
    public void testNoInterference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");

        WildFlyRootContext ctx1 = new WildFlyRootContext(props.clone());
        verifyAffinity(props, Affinity.NONE, new ClusterAffinity("test"));
        verifyAffinity(ctx1, Affinity.NONE, new ClusterAffinity("test"));

        ClusterAffinity bob = new ClusterAffinity("bob");
    }
}
