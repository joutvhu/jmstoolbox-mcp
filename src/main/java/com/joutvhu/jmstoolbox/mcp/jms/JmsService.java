package com.joutvhu.jmstoolbox.mcp.jms;

import com.joutvhu.jmstoolbox.mcp.config.model.ConnectionConfig;
import com.joutvhu.jmstoolbox.mcp.config.model.QManagerDef;
import com.joutvhu.jmstoolbox.mcp.driver.ConnectionFactoryBuilder;
import com.joutvhu.jmstoolbox.mcp.driver.JmsDriverRegistry;
import com.joutvhu.jmstoolbox.mcp.jms.model.JmsMessageDto;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JmsService {
    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public synchronized void connect(ConnectionConfig config, QManagerDef qManagerDef) throws Exception {
        String name = config.getName();
        if (activeConnections.containsKey(name)) {
            return; // Already connected
        }

        ClassLoader cl = JmsDriverRegistry.getInstance().getClassLoaderForQManager(qManagerDef);
        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Connection conn = null;
        Session session = null;
        try {
            Thread.currentThread().setContextClassLoader(cl);
            conn = ConnectionFactoryBuilder.createConnection(config, cl);
            conn.start();
            session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            activeConnections.put(name, conn);
            activeSessions.put(name, session);
            System.err.println("[JmsService] Successfully connected to " + name);
        } catch (Exception e) {
            // Clean up on partial connection failure
            if (session != null) {
                try { session.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
            throw e;
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }
    }

    public synchronized void disconnect(String name) {
        Session session = activeSessions.remove(name);
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
        }
        Connection conn = activeConnections.remove(name);
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
        System.err.println("[JmsService] Disconnected " + name);
    }

    public synchronized void disconnectAll() {
        for (String name : new ArrayList<>(activeConnections.keySet())) {
            disconnect(name);
        }
    }

    public boolean isConnected(String name) {
        return activeConnections.containsKey(name);
    }

    public Set<String> getActiveConnectionNames() {
        return Collections.unmodifiableSet(activeConnections.keySet());
    }

    public List<JmsMessageDto> browseMessages(String connectionName, String queueName, int maxMessages) throws Exception {
        Session session = getSession(connectionName);
        Queue queue = session.createQueue(queueName);
        QueueBrowser browser = session.createBrowser(queue);

        List<JmsMessageDto> results = new ArrayList<>();
        try {
            Enumeration<?> msgs = browser.getEnumeration();
            int count = 0;
            int limit = maxMessages > 0 ? maxMessages : 10;
            while (msgs.hasMoreElements() && count < limit) {
                Message msg = (Message) msgs.nextElement();
                results.add(JmsMessageDto.fromJmsMessage(msg));
                count++;
            }
        } finally {
            try { browser.close(); } catch (Exception ignored) {}
        }
        return results;
    }

    public int getQueueDepth(String connectionName, String queueName) throws Exception {
        Session session = getSession(connectionName);
        Queue queue = session.createQueue(queueName);
        QueueBrowser browser = session.createBrowser(queue);

        int count = 0;
        try {
            Enumeration<?> msgs = browser.getEnumeration();
            while (msgs.hasMoreElements()) {
                msgs.nextElement();
                count++;
            }
        } finally {
            try { browser.close(); } catch (Exception ignored) {}
        }
        return count;
    }

    public String sendMessage(String connectionName, String queueName, String body, Map<String, Object> properties) throws Exception {
        Session session = getSession(connectionName);
        Queue queue = session.createQueue(queueName);
        MessageProducer producer = session.createProducer(queue);

        try {
            TextMessage textMessage = session.createTextMessage(body);
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    textMessage.setObjectProperty(entry.getKey(), entry.getValue());
                }
            }
            producer.send(textMessage);
            return textMessage.getJMSMessageID();
        } finally {
            try { producer.close(); } catch (Exception ignored) {}
        }
    }

    public JmsMessageDto consumeMessage(String connectionName, String queueName, long timeoutMs) throws Exception {
        Session session = getSession(connectionName);
        Queue queue = session.createQueue(queueName);
        MessageConsumer consumer = session.createConsumer(queue);

        try {
            Message msg = consumer.receive(timeoutMs > 0 ? timeoutMs : 1000);
            return JmsMessageDto.fromJmsMessage(msg);
        } finally {
            try { consumer.close(); } catch (Exception ignored) {}
        }
    }

    private Session getSession(String connectionName) {
        Session session = activeSessions.get(connectionName);
        if (session == null) {
            throw new java.lang.IllegalStateException("Connection '" + connectionName + "' is not active. Please call connect first.");
        }
        return session;
    }
}
