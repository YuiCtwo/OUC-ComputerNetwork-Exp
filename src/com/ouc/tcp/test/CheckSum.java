package com.ouc.tcp.test;

import java.util.ArrayList;

import java.util.zip.CRC32;

import com.ouc.tcp.message.TCP_HEADER;
import com.ouc.tcp.message.TCP_PACKET;
import com.ouc.tcp.message.MSG_STREAM;
public class CheckSum {
	
	// 计算TCP校验核
	public static short computeChkSum(TCP_PACKET tcpPack) {
		int checkSum = 0;
		TCP_HEADER h = tcpPack.getTcpH();
		/*
		byte [] fake_head = new byte[12];
		byte [] source = tcpPack.getSourceAddr().getAddress();
		byte [] target = tcpPack.getDestinAddr().getAddress();
		byte placeholder = (byte)0;
		byte protocol = (byte)6;
		int length = h.getTh_Length();
		for (int i = 0; i < 4; i++) {
			fake_head[i] = source[i];
			fake_head[i+4] = target[i];
		}
		fake_head[8] = placeholder;
		fake_head[9] = protocol;
		fake_head[10] = (byte)(length >> 8);
		fake_head[11] = (byte)((length << 24) >> 24);
		MSG_STREAM msg_stream = new MSG_STREAM(tcpPack);

		byte[] pkt_stream = msg_stream.getPacket_byteStream();
		byte[] result = new byte[fake_head.length + pkt_stream.length];
		System.arraycopy(fake_head, 0, result, 0, fake_head.length);
		System.arraycopy(msg_stream.getPacket_byteStream(), 0, result, 12, pkt_stream.length);

		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(result, 0, result.length);
		checkSum = (int)crc.getValue();

		 */

		CRC32 crc = new CRC32();
		crc.reset();
		crc.update(tcpPack.getSourceAddr().getAddress());
		crc.update(tcpPack.getDestinAddr().getAddress());
		crc.update(0);
		crc.update(6);
		crc.update(h.getTh_Length());
		crc.update(h.getTh_sport());
		crc.update(h.getTh_dport());
		crc.update(h.getTh_seq());
		crc.update(h.getTh_ack());
		crc.update(h.getTh_doff());
		// 保留位?
		crc.update(h.getTh_win());
		crc.update(h.getTh_urp());
		crc.update(h.getTh_mss());
		// 填充?


		// 很奇怪，没法校验tcp头的错误
		// 与原始的校验方法实现的不一样
		// 有空自己改改
		for (int i = 0; i < tcpPack.getTcpS().getData().length; i++) {
			crc.update(tcpPack.getTcpS().getData()[i]);
		}
		checkSum = (int)crc.getValue();
		return (short) checkSum;
	}
	
}
