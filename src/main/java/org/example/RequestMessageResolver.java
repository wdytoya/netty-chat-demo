package org.example;

import org.example.demo.protos.ChatMsg;

public class RequestMessageResolver implements Resolver {
    @Override
    public boolean support(ChatMsg msg) {
        return msg.getMsgType() == ChatMsg.MsgType.MSG_TYPE_REQUEST;
    }

    @Override
    public ChatMsg resolve(ChatMsg msg) {
        System.out.println("["+msg.getSessionId() + "]-[" + msg.getMsgType() + " type message]:"
                +msg.getMsgBody());
        // 处理完成后，生成一个响应消息返回
        ChatMsg response = ChatMsgFactory.buildMsg(ChatMsg.MsgType.MSG_TYPE_RESPONSE, "Confirmed your message " + msg.getSessionId() +" ...");

        return response;
    }
}
