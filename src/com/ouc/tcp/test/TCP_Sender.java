/***************************4.x: 拥塞控制Reno版本;
**************************** Ctwo; 2020-12-21*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;

import java.util.*;
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

enum CongestionController {
	SlowStart,           // 慢开始
	CongestionAvoidance, // 拥塞避免
	FastRetransmit,      // 快重传
	FastRecovery,        // 快恢复
}

public class TCP_Sender extends TCP_Sender_ADT {
	
	private TCP_PACKET tcpPack;	 // 待发送的TCP数据报
	private TCP_PACKET rcvPack;  // 已经收到的recv_pkt

	// 计时器
	private UDT_Timer timer;

	// 往返时延
	private int RTT = 1000;

	// 由于无法获取所有的包,模拟TCP发方缓冲
	// 发送的数据包的缓冲区
	private Queue<TCP_PACKET> packets_buffer;

	private HashMap<Integer, TCP_PACKET>record;

	// 拥塞窗口大小
	private short cwnd = 1;
	private float cwnd_tmp = 1;

	private short ssthresh = 16;

	// 窗口的初始位置
	private int send_base = 1;

	// 下一个要发的包的位置
	private int next_seq = 1;
	// 下一个要重发的包的位置
	private int next_resend_seq = 1;

	// Reno版本超时时执行的Task
	private RenoTimerTask task;

	// 默认从慢开始开始
	private volatile CongestionController pattern = CongestionController.SlowStart;

	// 当前 ack 的包
	private int ack_cache = 0;

	// ack 的次数
	private int ack_times;

	// 超时重发状态,此时阻塞由应用层的调用
	private boolean resend = false;

	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
		timer = new UDT_Timer();
		packets_buffer = new LinkedBlockingQueue<TCP_PACKET>();

		//设置错误控制标志
		tcpH.setTh_eflag((byte) FlagType.RealEnv.ordinal());

		// 全局所有发的包的记录
		record = new HashMap<Integer, TCP_PACKET>();
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报
	public void rdt_send(int dataIndex, int[] appData) {
		// appData 要发送的数据
		// dataIndex 要发的数据的索引
		while (next_seq >= send_base + cwnd || resend){
			// 等待收到ack后窗口的移动
			// 或者等待未发送的包都全部先送出去
		}
		// next_seq < send_base + N

		// 设置一下tcpH的数据
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
		tcpH.setTh_seq(next_seq);//包序号设置为要发送的序号
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);

		try {
			record.put(next_seq, tcpPack.clone());
		} catch (CloneNotSupportedException ignored) {
		}
		//发送TCP数据报
		udt_send(tcpPack);

		// 添加到滑动窗口的数据中
		try {
			if(this.append(tcpPack)){
			}
			else {
				System.out.println("queue is full, this shouldn't has appeared!");
			}
		} catch (CloneNotSupportedException ignored) {
		}

		if (send_base == next_seq){
			// 设置超时重传
			// 3s后执行, 之后每隔3s执行
			task = new RenoTimerTask(this, packets_buffer);
			timer.schedule(task, 3000, 3000);

		}

		// 下一个要发送的包后移一位
		next_seq += 1;

	}


	@Override
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {

		//发送数据报
		client.send(stcpPack);
	}
	
	@Override
	public void waitACK() {

		int ack = this.rcvPack.getTcpH().getTh_ack();
		System.out.println("send_base: " + this.send_base);
//		if (ack == this.ack_cache){
//			ack_times += 1;
//			if (ack_times == 4){
//				// 连续收到3个对同一个报文的ack
//				// 执行快重传 ack+1 序列的包
//				TCP_PACKET pkt = record.get(ack_cache + 1);
//				System.out.println("3-ack, resend pkt: " + pkt.getTcpH().getTh_seq());
//				udt_send(pkt);
//
//				System.out.println("Windows size in sender: " + this.cwnd);
//				// 拥塞窗口减半
//				cwnd = (short) (cwnd / 2);
//				cwnd_tmp = cwnd;
//				ssthresh = (short) Math.max(ssthresh / 2, 2);
//				System.out.println("CongestionAvoidance used!");
//				System.out.println("Windows size become:" + cwnd);
//				System.out.println("ssthresh size become:" + ssthresh);
//
//				// 设置为拥塞避免
//				pattern = CongestionController.CongestionAvoidance;
//			}
//		}
//		else {
//			this.ack_cache = ack;
//			ack_times = 1;
//		}
		if (ack >= send_base){

			// 计算滑动的位数
			int d = (ack + 1) - send_base;

			// 新的等待ack的
			send_base = ack + 1;

			// 窗口的滑动操作
			this.slide(d);

			System.out.println("Clear: "+ ack);

			// 记录收到的包
			ackQueue.add(rcvPack.getTcpH().getTh_ack());

			// 是否重置计时器
			if (send_base == next_seq){
				// 全部包已经ack
				task.cancel();
			}
			else {
				// 重启task
				task.cancel();
				task = new RenoTimerTask(this, packets_buffer);
				timer.schedule(this.task, 3000, 3000);
			}

			// 对于新确认的包才增加窗口
			if (cwnd >= ssthresh){
				pattern = CongestionController.CongestionAvoidance;
				cwnd_tmp += 1.0/cwnd;
				cwnd = (short) cwnd_tmp;
			}
			else {
				// 仍处于慢开始,收一个ack cwnd 加1
				cwnd += 1;
				cwnd_tmp += 1;
			}
			System.out.println("Windows size="+ cwnd);
			System.out.println("ssthresh size=" + ssthresh);
		}
		else{
			System.out.println("An outdated ack");
		}

//		if (cwnd == ssthresh){
//			pattern = CongestionController.CongestionAvoidance;
//			cwnd_tmp += 1.0/cwnd;
//			cwnd = (short) cwnd_tmp;
//		}
//		else {
//			// 仍处于慢开始,收一个ack cwnd 加1
//			cwnd += 1;
//			cwnd_tmp += 1;
//		}

		if (resend){
			System.out.println("===Sender:Resend start with seq: " + next_resend_seq);
			System.out.println("packets buffed:");
			for (TCP_PACKET pkt: packets_buffer) {
				System.out.println(pkt.getTcpH().getTh_seq());
			}
			if (send_base > next_resend_seq){
				next_resend_seq = send_base;
				System.out.println("Fast send_base move!!");
			}
			else {
				while (next_resend_seq < send_base + cwnd){
					// 佛了,获取太麻烦了就这样偷懒吧
					for (TCP_PACKET pkt: packets_buffer) {
						if (pkt.getTcpH().getTh_seq() == next_resend_seq){
							udt_send(pkt);
						}
					}
					next_resend_seq += 1;
				}
				System.out.println("Early send pkt may lost in routing");
			}

			System.out.println("====Sender:Resend end in seq: " + next_resend_seq);
			if (next_resend_seq == next_seq){
				// 丢失的包全部被发了出去
				resend = false;
			}
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
		if (this.packets_buffer.size() < this.cwnd){
			this.packets_buffer.offer(pkt.clone());
			return true;
		}
		else{
			return false;
		}
	}

	private void slide(int s){
		// 窗口滑动s位
		for (int i = 0; i < s; i++) {
			this.packets_buffer.poll();
		}
	}

	public void setPattern(CongestionController pattern) {
		this.pattern = pattern;
	}

	public void setCwnd(short cwnd) {
		this.cwnd = cwnd;
		this.cwnd_tmp = cwnd;
	}

	public short getCwnd() {
		return cwnd;
	}

	public void setSsthresh(short ssthresh) {
		// 确保最小值为 2
		this.ssthresh = (short) Math.max(ssthresh, 2);
	}

	public void setResend(boolean resend) {
		this.resend = resend;
	}

	public void setNext_resend_seq() {
		this.next_resend_seq = this.send_base + 1;
	}

	public int getSend_base() {
		return send_base;
	}

	public void setSend_base(int send_base) {
		this.send_base = send_base;
	}

}
