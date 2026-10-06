package org.example.codec;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import org.example.config.ServerConfig;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;

import java.util.UUID;

/**
 * Builds top-level Envelope messages for Chat / Room domains.
 */
public final class EnvelopeFactory {

    private EnvelopeFactory() {
    }

    public static Envelope wrap(Domain domain, int msgType, Message payload) {
        return wrap(domain, msgType, payload, UUID.randomUUID().toString());
    }

    public static Envelope wrap(Domain domain, int msgType, Message payload, String requestId) {
        Envelope.Builder builder = Envelope.newBuilder()
                .setVersion(ServerConfig.PROTOCOL_VERSION)
                .setDomain(domain)
                .setMsgType(msgType)
                .setPayload(ByteString.copyFrom(payload.toByteArray()));
        if (requestId != null && !requestId.isEmpty()) {
            builder.setRequestId(requestId);
        }
        return builder.build();
    }

    public static Envelope wrapEmptyPayload(Domain domain, int msgType) {
        return Envelope.newBuilder()
                .setVersion(ServerConfig.PROTOCOL_VERSION)
                .setRequestId(UUID.randomUUID().toString())
                .setDomain(domain)
                .setMsgType(msgType)
                .setPayload(ByteString.EMPTY)
                .build();
    }
}
