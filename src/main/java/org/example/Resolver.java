package org.example;

import org.example.demo.protos.ChatMsg;

public interface Resolver {
    public boolean support(ChatMsg msg);

    public ChatMsg resolve(ChatMsg msg);
}
