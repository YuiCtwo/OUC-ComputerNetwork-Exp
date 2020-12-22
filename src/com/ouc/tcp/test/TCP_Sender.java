/***************************3.0: 将考虑延时和丢包,此时仍是停止等待;
**************************** Ctwo; 2020-12-21*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.LinkedBlockingQueue;

enum FlagType {
	Stable,            //信道无差错
	ErrorOnly,         //只出错
	LossOnly,          //只丢包
	DelayOnly,         //只延迟
	ErrorWithLoss,     //出错 / 丢包
	ErrorWithDelay,    //出错 / 延迟
	LossWithDelay,     //丢包 / 延迟
	RealEnv,           //实际环境=出错 / 丢包 / 延迟
}

public class TCP_Sender extends TCP_Sender_ADT {
	
	private TCP_PACKET tcpPack;	//待发送的TCP数据报
	private volatile int flag = 0;
	private TCP_PACKET rcvPack;  // 已经收到的recv_pkt
	private UDT_Timer timer;
	private LinkedBlockingQueue<TCP_PACKET> packets;
	private short windows = 16;
	private int send_base = 1;
	private int next_seq = 1;
	private TaskPacketsRetransmit task;


	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
		timer = new UDT_Timer();
		packets = new LinkedBlockingQueue<TCP_PACKET>();
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		// appData 要发送的数据
		// dataIndex 要发的数据的索引
		while (next_seq >= send_base + windows){
			// 等待收到ack后窗口的移动
		}
		// next_seq < send_base + N
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(next_seq);//包序号设置为要发送的序号
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);

		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);

		//发送TCP数据报
		udt_send(tcpPack);

		// 添加到滑动窗口要重传的数据中
		try {
			if(this.append(tcpPack)){
//				System.out.println("Add success!");
			}
			else {
				System.out.println("queue is full, this shouldn't has appeared!");
			}
		} catch (CloneNotSupportedException ignored) {
		}

		if (send_base == next_seq){
			// 设置超时重传
			// 3s后执行, 之后每隔3s执行
			task = new TaskPacketsRetransmit(client, packets);
			timer.schedule(task, 3000, 3000);

		}
		// 下一个要发送的包后移一位
		next_seq += 1;
	}
	
	@Override
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		//设置错误控制标志

		tcpH.setTh_eflag((byte) FlagType.RealEnv.ordinal());
		//System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());				
		//发送数据报
		client.send(stcpPack);
	}
	
	@Override
	//需要修改
	public void waitACK() {

		int ack = this.rcvPack.getTcpH().getTh_ack();
		if (ack >= send_base){
			int d = (ack + 1) - send_base;
			send_base = ack + 1;

			// 窗口的滑动操作
			this.slide(d);

			// 是否重置计时器
			if (send_base == next_seq){
				// 全部包已经ack
				task.cancel();
			}
			else {
				// 重启task
				task.cancel();
				task = new TaskPacketsRetransmit(client, packets);
				timer.schedule(this.task, 3000, 3000);

			}
			System.out.println("Clear: "+ack);

			// 记录收到的包
			ackQueue.add(rcvPack.getTcpH().getTh_ack());
		}
		else{
			System.out.println("An outdated ack");
		}
	}

	@Override
	//接收到ACK报文：检查校验和，将确认号插入ack队列;
	public void recv(TCP_PACKET recvPack) {
		System.out.println("Receive ACK Number： "+ recvPack.getTcpH().getTh_ack());
		// ACK包不出错
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			// 重复的包也一样塞到队列里,在waitACK里面统一处理
			this.rcvPack = recvPack;
			//处理ACK报文
			waitACK();
		}
		else {
			// ack包出错,等待重传
			System.out.println("Wrong ack pkt, waiting for timeout");

		}
		System.out.println();
	}



	// 对 pkt_queue 的操作
	private boolean append(TCP_PACKET pkt) throws CloneNotSupportedException {
		// 存入 packets 中的时候必须要深复制,不然都变成一样的了
		if (this.packets.size() < this.windows){
			this.packets.offer(pkt.clone());
			return true;
		}
		else{
			return false;
		}
	}

	private void setWidth(short w){
		if (w > this.windows){
			// 不需要收缩
		}
		else {
			// 收缩
			this.resize_width();
		}
		this.windows = w;
	}

	private void resize_width(){
		while (this.packets.size() > this.windows){
			// poll() 方法从队列中删除第一个元素,即窗口向右收缩
			this.packets.poll();
		}
	}

	private void slide(int s){
		// 窗口滑动s位
		for (int i = 0; i < s; i++) {
			this.packets.poll();
		}
	}
}
