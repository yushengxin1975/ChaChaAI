# -*- coding: utf-8 -*-
"""
Direct Bluetooth OBEX Object Push (OPP) client to send ChaChaAI to iFlytek EBook tablet.
Android BluetoothOpp blocks *.apk directly, so the file is transmitted as ChaChaAI.zip.
"""
import socket
import struct
import os
import sys
import time

BT_ADDR = '60:48:9c:46:9c:bb'
CHANNEL = 4
APK_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'ChaChaAI.apk'))
REMOTE_FILENAME = 'ChaChaAI.zip'

def main():
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    if not os.path.exists(APK_PATH):
        print(f"错误: 未找到 APK 文件: {APK_PATH}")
        sys.exit(1)

    file_size = os.path.getsize(APK_PATH)
    print(f"==================================================")
    print(f"目标设备: 起点讯飞阅读器 (iFlytek EBook)")
    print(f"蓝牙物理地址: {BT_ADDR}")
    print(f"OBEX 传输通道: Channel {CHANNEL}")
    print(f"本地原始文件: ChaChaAI.apk ({file_size / 1024 / 1024:.2f} MB, {file_size} 字节)")
    print(f"蓝牙传输文件名: {REMOTE_FILENAME} (避开安卓蓝牙对.apk的安全拦截)")
    print(f"==================================================")

    print("正在连接阅读器蓝牙服务...")
    s = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    s.settimeout(15)
    try:
        s.connect((BT_ADDR, CHANNEL))
    except Exception as e:
        print(f"连接失败: {e}")
        print("请确认：1. 阅读器蓝牙已开启；2. 阅读器屏幕亮起并未深度休眠。")
        sys.exit(1)

    print("蓝牙链路已连接，正在进行 OBEX 协商...")

    # 1. OBEX CONNECT
    connect_req = b'\x80\x00\x07\x10\x00\x20\x00'
    s.sendall(connect_req)
    resp = s.recv(1024)
    if not resp or resp[0] != 0xA0:
        print(f"OBEX 握手失败: {resp.hex() if resp else '无响应'}")
        s.close()
        sys.exit(1)

    max_pkt_len = struct.unpack('>H', resp[5:7])[0]
    if max_pkt_len < 255:
        max_pkt_len = 2048
    print(f"OBEX 握手成功！单包上限: {max_pkt_len} 字节")

    # 构建首包头部
    name_bytes = REMOTE_FILENAME.encode('utf-16be') + b'\x00\x00'
    name_hdr = b'\x01' + struct.pack('>H', len(name_bytes) + 3) + name_bytes
    length_hdr = b'\xc3' + struct.pack('>I', file_size)
    type_bytes = b'application/zip\x00'
    type_hdr = b'\x42' + struct.pack('>H', len(type_bytes) + 3) + type_bytes

    headers = name_hdr + length_hdr + type_hdr

    print("\n>>> 请立刻查看阅读器屏幕！<<<")
    print(">>> 正在发起传输，阅读器顶部将弹出【接收文件】通知，请点击【接收】！<<<")

    # 2. OBEX PUT (Streaming chunks)
    with open(APK_PATH, 'rb') as f:
        bytes_sent = 0
        first_packet = True
        start_time = time.time()

        # 等待用户在阅读器上点击“接收”，超时时间设为 90 秒
        s.settimeout(90)

        while bytes_sent < file_size:
            overhead = 3 + (len(headers) if first_packet else 0) + 3
            chunk_size = min(max_pkt_len - overhead, file_size - bytes_sent)
            payload = f.read(chunk_size)
            bytes_sent += len(payload)

            is_final = (bytes_sent >= file_size)
            opcode = 0x82 if is_final else 0x02
            body_hdr_id = b'\x49' if is_final else b'\x48'
            body_hdr = body_hdr_id + struct.pack('>H', len(payload) + 3) + payload

            packet_data = (headers if first_packet else b'') + body_hdr
            packet = bytes([opcode]) + struct.pack('>H', len(packet_data) + 3) + packet_data

            s.sendall(packet)
            first_packet = False

            try:
                put_resp = s.recv(512)
            except socket.timeout:
                print("\n等待阅读器响应超时（未在 90 秒内点击接收或断开连接）")
                s.close()
                sys.exit(1)

            if not put_resp:
                print("\n连接被阅读器端关闭")
                s.close()
                sys.exit(1)

            resp_code = put_resp[0]
            if is_final:
                if resp_code == 0xA0:
                    break
                else:
                    print(f"\n传输结束异常响应: 0x{resp_code:02X}")
                    s.close()
                    sys.exit(1)
            else:
                if resp_code != 0x90 and resp_code != 0xA0:
                    print(f"\n传输被拒绝或中断: 0x{resp_code:02X}")
                    s.close()
                    sys.exit(1)

            # 进入连续传输阶段后，超时时间设为 15 秒
            s.settimeout(15)

            elapsed = time.time() - start_time
            speed = (bytes_sent / 1024) / max(elapsed, 0.001)
            pct = (bytes_sent / file_size) * 100
            sys.stdout.write(f"\r正在传输: {pct:5.1f}% [{bytes_sent}/{file_size} 字节] 速度: {speed:5.1f} KB/s")
            sys.stdout.flush()

    total_time = time.time() - start_time
    print(f"\n\n==================================================")
    print(f"传输完成！总耗时: {total_time:.1f} 秒，平均速度: {(file_size/1024)/total_time:.1f} KB/s")
    print(f"文件已存入阅读器蓝牙接收目录: {REMOTE_FILENAME}")
    print("操作提示：请在阅读器【文件管理 -> 蓝牙】中将 ChaChaAI.zip 重命名为 ChaChaAI.apk 即可点击安装！")
    print("==================================================")

    # 3. OBEX DISCONNECT
    try:
        s.sendall(b'\x81\x00\x03')
        s.recv(128)
    except Exception:
        pass
    s.close()

if __name__ == '__main__':
    main()
