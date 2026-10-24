package com.joutvhu.jmstoolbox.mcp.jms.model;

import javax.jms.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class JmsMessageDto {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024; // 2MB limit for MCP safety

    private String messageId;
    private String correlationId;
    private String destination;
    private String replyTo;
    private Long timestamp;
    private Long expiration;
    private Integer priority;
    private Integer deliveryMode;
    private Boolean redelivered;
    private String type;
    private Map<String, Object> properties = new HashMap<>();
    private String body;

    public static JmsMessageDto fromJmsMessage(Message msg) {
        if (msg == null) return null;
        JmsMessageDto dto = new JmsMessageDto();
        try {
            dto.messageId = msg.getJMSMessageID();
            dto.correlationId = msg.getJMSCorrelationID();
            if (msg.getJMSDestination() != null) {
                dto.destination = msg.getJMSDestination().toString();
            }
            if (msg.getJMSReplyTo() != null) {
                dto.replyTo = msg.getJMSReplyTo().toString();
            }
            dto.timestamp = msg.getJMSTimestamp();
            dto.expiration = msg.getJMSExpiration();
            dto.priority = msg.getJMSPriority();
            dto.deliveryMode = msg.getJMSDeliveryMode();
            dto.redelivered = msg.getJMSRedelivered();
            dto.type = msg.getJMSType();

            // Properties
            Enumeration<?> propNames = msg.getPropertyNames();
            while (propNames.hasMoreElements()) {
                String name = (String) propNames.nextElement();
                dto.properties.put(name, msg.getObjectProperty(name));
            }

            // Body handling
            if (msg instanceof TextMessage) {
                dto.body = ((TextMessage) msg).getText();
            } else if (msg instanceof BytesMessage) {
                BytesMessage bm = (BytesMessage) msg;
                bm.reset();
                long length = bm.getBodyLength();
                if (length > MAX_BODY_BYTES) {
                    byte[] bytes = new byte[MAX_BODY_BYTES];
                    bm.readBytes(bytes);
                    dto.body = new String(bytes) + " ... [TRUNCATED - Payload exceeded 2MB]";
                } else {
                    byte[] bytes = new byte[(int) length];
                    bm.readBytes(bytes);
                    dto.body = new String(bytes);
                }
            } else if (msg instanceof MapMessage) {
                MapMessage mm = (MapMessage) msg;
                Map<String, Object> map = new HashMap<>();
                Enumeration<?> mapNames = mm.getMapNames();
                while (mapNames.hasMoreElements()) {
                    String k = (String) mapNames.nextElement();
                    map.put(k, mm.getObject(k));
                }
                dto.body = map.toString();
            } else if (msg instanceof ObjectMessage) {
                try {
                    dto.body = "[ObjectMessage: " + ((ObjectMessage) msg).getObject() + "]";
                } catch (Exception e) {
                    dto.body = "[ObjectMessage: Class not found on client classpath: " + e.getMessage() + "]";
                }
            } else {
                dto.body = "[Unsupported Message Format: " + msg.getClass().getName() + "]";
            }
        } catch (JMSException e) {
            System.err.println("[JmsMessageDto] Error converting message: " + e.getMessage());
        }
        return dto;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDestination() {
        return destination;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Long getExpiration() {
        return expiration;
    }

    public Integer getPriority() {
        return priority;
    }

    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    public Boolean getRedelivered() {
        return redelivered;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getBody() {
        return body;
    }
}
