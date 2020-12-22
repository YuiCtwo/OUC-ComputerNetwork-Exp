package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.LinkedList;
import java.util.Queue;
import java.util.TimerTask;

/**
 * @author ctwo
 *
 */
public class TaskPacketsRetransmit extends TimerTask {

    private Client senderClient;
    // 队列表示窗口, 高序号的包在队尾, 低序号的在队首
    private Queue<TCP_PACKET> packets;
    private TCP_PACKET pack;


    public TaskPacketsRetransmit(Client client, Queue<TCP_PACKET>pkt){
        super();
        senderClient = client;
        // 这里不要深复制
        packets = pkt;
    }

    @Override
    public void run() {
        for (TCP_PACKET pkt: packets) {
            this.senderClient.send(pkt);
            pack = pkt;
        }
        System.out.println();
        System.out.println("Task timeout!!");
        System.out.println("resend the pkt start with:"
                + packets.element().getTcpH().getTh_seq()
                + " end with:"
                + pack.getTcpH().getTh_seq());
    }
}
