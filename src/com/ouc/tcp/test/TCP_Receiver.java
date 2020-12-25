/******4.x: 拥塞控制Reno版本*****************/
/***** Ctwo; 2020-12-24******************************
 *  收方相较于go-back-n不发生变化,失序包发重复的确认
 */


package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;
import com.ouc.tcp.test.FlagType;


public class TCP_Receiver extends TCP_Receiver_ADT {
	
	private TCP_PACKET ackPack;	    //回复的ACK报文段
	int expect_seq;                 //用于记录当前期待的包序号
	LinkedList<Integer>seq_buffer;  //接收方的包序号缓存
		
	/*构造函数*/
	public TCP_Receiver() {
		super();	//调用超类构造函数
		super.initTCP_Receiver(this);	//初始化TCP接收端
		seq_buffer = new LinkedList<Integer>();
		expect_seq = 1;
	}

	@Override
	//接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {

		// 检查校验码，生成ACK
		if(CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {

			// 生成ACK报文段（设置确认号）
			int seq = recvPack.getTcpH().getTh_seq();
			System.out.println("Receive seq: " + seq);
			System.out.println("Expect seq: " + expect_seq);
			if (seq < expect_seq){

				// 一个重复发的包,直接丢弃
				System.out.println("Duplicate pkt:" + recvPack.getTcpH().getTh_seq());
				System.out.println("Expected pkt:" + recvPack.getTcpH().getTh_seq());
				// 依旧给一个回复
				tcpH.setTh_ack(expect_seq-1);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				reply(ackPack);
				return;
			}

			// 缓存收到的包,如果key相同就覆盖
			recvBuffer.put(seq, recvPack.getTcpS().getData());

			// 选择插入排序
			int index = 0;
			for (int s: seq_buffer) {
				if (s > seq){
					break;
				}
				else {
					index += 1;
				}
			}
			seq_buffer.add(index, seq);


			// 跨过连续的缓存的包,计算下一个期待的包
			int next_seq = expect_seq;
			System.out.println("====Receiver:New seq compute start====");
			System.out.println("seq in cached: ");
			for (int p: seq_buffer) {
				System.out.println(p);
			}

			// 判断链表首元素是否为我们要的序列, 是就弹出, 期待包序列加一
			while (seq_buffer.size() != 0 && seq_buffer.getFirst() == next_seq){

				next_seq += 1;
				// 移除首元素,相当于窗口滑动
				seq_buffer.poll();
			}

			System.out.println("compute newest seq: " + next_seq);
			System.out.println("seq in cached left: ");
			for (int p: seq_buffer) {
				System.out.println(p);
			}
			System.out.println("====Receiver:New seq compute end====");
			// 将接收到的正确有序的数据插入data队列，准备交付
			for (int i = expect_seq; i < next_seq; i++) {
				dataQueue.add(recvBuffer.get(i));
			}

			// 将下一个期望的包移动到第一个不连续的位置
			expect_seq = next_seq;

			// 回复最大的有序的包
			tcpH.setTh_ack(expect_seq-1);
			ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
			tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
			reply(ackPack);


		}else{
			System.out.println("An Error pkt: " + recvPack.getTcpH().getTh_seq());
			System.out.println("Recieve Computed: "+CheckSum.computeChkSum(recvPack));
			System.out.println("Recieved Packet"+recvPack.getTcpH().getTh_sum());
			// 啥都不用做,等待发方重传就完事了
		}

		System.out.println();
		
		
		//交付数据（每20组数据交付一次）
		if(dataQueue.size() == 20) 
			deliver_data();	
	}

	@Override
	//交付数据（将数据写入文件）；不需要修改
	public void deliver_data() {
		//检查dataQueue，将数据写入文件
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			
			//循环检查data队列中是否有新交付数据
			while(!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();
				
				//将数据写入文件
				for(int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				
				writer.flush();		//清空输出缓存
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	//回复ACK报文段
	public void reply(TCP_PACKET replyPack) {

		//设置错误控制标志
		tcpH.setTh_eflag((byte)FlagType.RealEnv.ordinal());	//eFlag=枚举类型
				
		//发送数据报
		client.send(replyPack);
	}
	
}
