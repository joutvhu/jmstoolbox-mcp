package com.joutvhu.jmstoolbox.mcp.driver;

import com.joutvhu.jmstoolbox.mcp.config.model.ConnectionConfig;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Dynamically builds and configures ConnectionFactory instances using reflection or JNDI,
 * matching known broker connection factory classes and property naming conventions.
 */
public class ConnectionFactoryBuilder {

    // Known Factory Classes per QManager ID prefix or keywords
    private static final Map<String, String[]> FACTORY_CANDIDATES = new LinkedHashMap<>();

    static {
        // ActiveMQ Classic
        FACTORY_CANDIDATES.put("activemq", new String[]{
                "org.apache.activemq.ActiveMQConnectionFactory",
                "org.apache.activemq.spring.ActiveMQConnectionFactory"
        });
        // ActiveMQ Artemis
        FACTORY_CANDIDATES.put("artemis", new String[]{
                "org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory",
                "org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory"
        });
        // IBM MQ
        FACTORY_CANDIDATES.put("ibmmq", new String[]{
                "com.ibm.mq.jms.MQQueueConnectionFactory",
                "com.ibm.mq.jms.MQConnectionFactory",
                "com.ibm.msg.client.jms.JmsConnectionFactory"
        });
        // RabbitMQ JMS Client
        FACTORY_CANDIDATES.put("rabbitmq", new String[]{
                "com.rabbitmq.jms.admin.RMQConnectionFactory"
        });
        // TIBCO EMS
        FACTORY_CANDIDATES.put("tibco", new String[]{
                "com.tibco.tibjms.TibjmsConnectionFactory",
                "com.tibco.tibjms.TibjmsQueueConnectionFactory"
        });
        // Solace PubSub+
        FACTORY_CANDIDATES.put("solace", new String[]{
                "com.solacesystems.jms.SolConnectionFactoryImpl"
        });
        // OpenMQ
        FACTORY_CANDIDATES.put("openmq", new String[]{
                "com.sun.messaging.ConnectionFactory",
                "com.sun.messaging.QueueConnectionFactory"
        });
        // SonicMQ
        FACTORY_CANDIDATES.put("sonic", new String[]{
                "progress.message.jclient.ConnectionFactory",
                "progress.message.jclient.QueueConnectionFactory"
        });
        // Oracle WebLogic JMS
        FACTORY_CANDIDATES.put("weblogic", new String[]{
                "weblogic.jms.client.JMSConnectionFactory",
                "weblogic.jndi.WLInitialContextFactory"
        });
    }

    public static Connection createConnection(ConnectionConfig config, ClassLoader classLoader) throws Exception {
        ConnectionFactory factory = buildFactory(config, classLoader);
        if (factory == null) {
            throw new IllegalStateException("Could not create ConnectionFactory for: " + config.getQManagerDef());
        }

        String user = config.getUserid();
        String pass = config.getPassword();

        if (user != null && !user.trim().isEmpty() && pass != null) {
            return factory.createConnection(user, pass);
        } else {
            return factory.createConnection();
        }
    }

    public static ConnectionFactory buildFactory(ConnectionConfig config, ClassLoader classLoader) throws Exception {
        // 1. Check if configured via JNDI (e.g. WebLogic, WebSphere, JNDI lookup)
        String jndiFactory = config.getProperties().get("java.naming.factory.initial");
        String jndiLookup = config.getProperties().get("jndiConnectionFactoryName");
        if (jndiLookup == null) {
            jndiLookup = config.getProperties().get("connectionFactoryJNDIName");
        }

        if (jndiFactory != null && jndiLookup != null) {
            Hashtable<String, String> env = new Hashtable<>(config.getProperties());
            if (!env.containsKey(Context.PROVIDER_URL)) {
                String providerUrl = config.getProperties().get("brokerURL");
                if (providerUrl == null) {
                    providerUrl = "t3://" + config.getHost() + ":" + config.getPort();
                }
                env.put(Context.PROVIDER_URL, providerUrl);
            }
            if (config.getUserid() != null) {
                env.put(Context.SECURITY_PRINCIPAL, config.getUserid());
            }
            if (config.getPassword() != null) {
                env.put(Context.SECURITY_CREDENTIALS, config.getPassword());
            }
            InitialContext ctx = new InitialContext(env);
            return (ConnectionFactory) ctx.lookup(jndiLookup);
        }

        String qmId = config.getQManagerDef() != null ? config.getQManagerDef().toLowerCase() : "";
        List<String> candidateClasses = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : FACTORY_CANDIDATES.entrySet()) {
            if (qmId.contains(entry.getKey())) {
                candidateClasses.addAll(Arrays.asList(entry.getValue()));
            }
        }

        // Add user specified connection factory class in properties if any
        if (config.getProperties().containsKey("connectionFactoryClass")) {
            candidateClasses.add(0, config.getProperties().get("connectionFactoryClass"));
        }

        Class<?> factoryClazz = null;
        for (String className : candidateClasses) {
            try {
                factoryClazz = Class.forName(className, true, classLoader);
                if (factoryClazz != null) break;
            } catch (ClassNotFoundException ignored) {}
        }

        if (factoryClazz == null) {
            throw new ClassNotFoundException("No viable ConnectionFactory class found on classpath for QManager: " + config.getQManagerDef()
                    + ". Please ensure driver JARs are configured in JMSToolBox or set JTB_HOME.");
        }

        Object factoryInstance = null;

        // Try brokerURL constructor if available (ActiveMQ / Artemis)
        String brokerUrl = config.getProperties().get("brokerURL");
        if (brokerUrl == null || brokerUrl.isEmpty()) {
            boolean ssl = config.getHost().startsWith("ssl://") || "true".equalsIgnoreCase(config.getProperties().get("useSSL"));
            String scheme = ssl ? "ssl" : "tcp";
            brokerUrl = scheme + "://" + config.getHost() + ":" + config.getPort();
        }

        try {
            Constructor<?> urlCtor = factoryClazz.getConstructor(String.class);
            factoryInstance = urlCtor.newInstance(brokerUrl);
        } catch (NoSuchMethodException ignored) {
            // Default no-arg constructor
            factoryInstance = factoryClazz.getDeclaredConstructor().newInstance();
        }

        // Populate common properties
        setPropertySilently(factoryInstance, "host", config.getHost());
        setPropertySilently(factoryInstance, "hostName", config.getHost());
        setPropertySilently(factoryInstance, "port", config.getPort());
        setPropertySilently(factoryInstance, "brokerURL", brokerUrl);

        if (config.getUserid() != null) {
            setPropertySilently(factoryInstance, "userName", config.getUserid());
            setPropertySilently(factoryInstance, "user", config.getUserid());
        }
        if (config.getPassword() != null) {
            setPropertySilently(factoryInstance, "password", config.getPassword());
        }

        // Specific handling for IBM MQ
        if (qmId.contains("ibmmq")) {
            String qmgr = config.getProperties().get("queueManager");
            if (qmgr != null) setPropertySilently(factoryInstance, "queueManager", qmgr);
            String channel = config.getProperties().get("channel");
            if (channel != null) setPropertySilently(factoryInstance, "channel", channel);
            setPropertySilently(factoryInstance, "transportType", 1); // WMQ_CM_CLIENT
            String cipherSuite = config.getProperties().get("sslCipherSuite");
            if (cipherSuite != null) setPropertySilently(factoryInstance, "sslCipherSuite", cipherSuite);
        }

        // Apply custom properties from config
        for (Map.Entry<String, String> prop : config.getProperties().entrySet()) {
            setPropertySilently(factoryInstance, prop.getKey(), prop.getValue());
        }

        // Fallback: also try direct method setter if JavaBean property descriptor missed it
        applyDirectMethods(factoryInstance, config);

        return (ConnectionFactory) factoryInstance;
    }

    private static void applyDirectMethods(Object target, ConnectionConfig config) {
        if (target == null) return;
        invokeMethodSilently(target, "setHostName", String.class, config.getHost());
        invokeMethodSilently(target, "setPort", int.class, config.getPort());
        invokeMethodSilently(target, "setPortNumber", int.class, config.getPort());
        if (config.getUserid() != null) {
            invokeMethodSilently(target, "setUserName", String.class, config.getUserid());
            invokeMethodSilently(target, "setUser", String.class, config.getUserid());
        }
        if (config.getPassword() != null) {
            invokeMethodSilently(target, "setPassword", String.class, config.getPassword());
        }
    }

    private static void invokeMethodSilently(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Exception ignored) {}
    }

    private static void setPropertySilently(Object target, String propName, Object value) {
        if (target == null || propName == null || value == null) return;
        try {
            PropertyDescriptor pd = new PropertyDescriptor(propName, target.getClass());
            Method writeMethod = pd.getWriteMethod();
            if (writeMethod != null) {
                Class<?> paramType = writeMethod.getParameterTypes()[0];
                Object converted = convertValue(value, paramType);
                writeMethod.invoke(target, converted);
            }
        } catch (Exception ignored) {
            // Property not supported by this specific vendor factory - safe to ignore
        }
    }

    private static Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) return value;
        String str = value.toString();
        if (targetType == String.class) return str;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        return value;
    }
}
