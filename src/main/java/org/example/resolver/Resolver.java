package org.example.resolver;

import io.netty.channel.ChannelHandlerContext;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;

/**
 * Handles one (Domain, msgType) pair.
 */
public interface Resolver {
    boolean support(Domain domain, int msgType);

    void resolve(ChannelHandlerContext ctx, Envelope envelope) throws Exception;
}
