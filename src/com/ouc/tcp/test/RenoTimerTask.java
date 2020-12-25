package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TimerTask;

/**
 * @author ctwo
 *
 */
public class RenoTimerTask extends TimerTask {
    /*
    TCP Reno 版本的计时器任务
    当超时时拥塞窗口减为1,发一个包出去,设置模式为重发模式
     */

    private TCP_Sender senderClient;
    private Queue<TCP_PACKET> packets;


    public RenoTimerTask(TCP_Sender sender, Queue<TCP_PACKET> pkts){
        super();
        senderClient = sender;
        packets = pkts;
    }

    @Override
    public void run() {

        System.out.println("Net Congestion Detected!");
        System.out.println("Windows size in sender: " + senderClient.getCwnd());

        // 延时超时,慢开始
        senderClient.setPattern(CongestionController.SlowStart);

        // 设置重发
        senderClient.setResend(true);

        // 慢开始设置窗口大小为1
        senderClient.setCwnd((short) 1);

        // 新ssthresh为原来的窗口大小的一半
        senderClient.setSsthresh((short) (senderClient.getCwnd() / 2));

        // 发一个包
        Iterator<TCP_PACKET> pkt = packets.iterator();
        TCP_PACKET t = pkt.next();
        senderClient.udt_send(t);

        // 设置标志位
        senderClient.setSend_base(t.getTcpH().getTh_seq());
        senderClient.setNext_resend_seq();

        System.out.println("Start SlowStart while sending the pkt: " + t.getTcpH().getTh_seq());



    }
}
