/***************************3.0: 将考虑延时和丢包,此时仍是停止等待;
**************************** Ctwo; 2020-12-21*/

package com.ouc.tcp.test;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;
import com.ouc.tcp.tool.TCP_TOOL;

import java.util.Enumeration;

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
	private Boolean canceled = false;

	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);//包序号设置为字节流号：
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);		
				
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		
		//发送TCP数据报
		udt_send(tcpPack);
		flag = 0;

		// 设置超时重传
		timer = new UDT_Timer();

		UDT_RetransTask task = new UDT_RetransTask(client, tcpPack);
		// 3s后执行, 之后每隔3s执行
		timer.schedule(task, 3000, 3000);
		canceled = false;
		//等待ACK报文
		//停止等待;
		while (flag==0);
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
		//循环检查ackQueue
		//循环检查确认号对列中是否有新收到的ACK		
		if(!isAck(this.rcvPack.getTcpH().getTh_ack())){
			// get the head of this queue, but does not remove
			// System.out.println("CurrentAck: "+currentAck);
			System.out.println("Clear: "+tcpPack.getTcpH().getTh_seq());
			flag = 1;
			// 收到ack,取消计时器timer的重发计时
			timer.cancel();
			canceled = true;

			// 记录收到的包
			ackQueue.add(rcvPack.getTcpH().getTh_ack());

		}
		else{
			// 是一个已经被ACK的包,等待计时器超时再重发
			if (canceled){
				System.out.println("Delay caused the resent");
			}
			else {
				System.out.println("Acked pkt, waiting for timeout!");
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
			if (canceled){
				System.out.println("Delay caused the resent");
			}
			else {
				System.out.println("Wrong Ack, waiting for timeout");
				// ACK包出错, 等待计时器超时自动重发
			}

		}
		System.out.println();
	}

	private boolean isAck(int ack){
		for (int x:ackQueue) {
			if (x == ack){
				return true;
			}
		}
		return false;
	}
}
