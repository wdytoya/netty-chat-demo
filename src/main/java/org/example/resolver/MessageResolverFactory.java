package org.example.resolver;

import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routes Envelope to a Resolver by (Domain, msgType).
 */
public class MessageResolverFactory {
    private static final MessageResolverFactory INSTANCE = new MessageResolverFactory();
    private final List<Resolver> resolvers = new CopyOnWriteArrayList<>();

    private MessageResolverFactory() {
    }

    public static MessageResolverFactory getInstance() {
        return INSTANCE;
    }

    public void registerResolver(Resolver resolver) {
        resolvers.add(resolver);
    }

    public Resolver getMessageResolver(Envelope envelope) {
        Domain domain = envelope.getDomain();
        int msgType = envelope.getMsgType();
        for (Resolver resolver : resolvers) {
            if (resolver.support(domain, msgType)) {
                return resolver;
            }
        }
        throw new IllegalArgumentException(
                "cannot find resolver, domain=" + domain + ", msgType=" + msgType);
    }
}
