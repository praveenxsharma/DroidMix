#!/usr/bin/env python3
import argparse
import socket
import subprocess
import sys
import signal
import time
import threading
import os

SAMPLE_RATE = 48000
CHANNELS = 1
BIT_DEPTH = 16
CHUNK_BYTES = 1024 * 4

def create_null_sink(name: str) -> int | None:
    cmd = [
        'pactl', 'load-module', 'module-null-sink',
        f'sink_name={name}',
        f'sink_properties=device.description="{name} (DroidMix)"',
        f'rate={SAMPLE_RATE}',
        f'channels={CHANNELS}',
        'format=s16le'
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"[sink] Error creating sink: {result.stderr.strip()}", file=sys.stderr)
            return None
        module_id = int(result.stdout.strip())
        print(f"[sink] Created '{name}' (module #{module_id})")
        print(f"[sink] Virtual mic: {name}.monitor")
        return module_id
    except Exception as e:
        print(f"[sink] Failed to run pactl: {e}", file=sys.stderr)
        return None

def remove_null_sink(module_id: int):
    subprocess.run(['pactl', 'unload-module', str(module_id)], capture_output=True)
    print(f"[sink] Removed module #{module_id}")

def start_pacat(sink_name: str) -> subprocess.Popen:
    cmd = [
        'pacat',
        '--playback',
        f'--device={sink_name}',
        '--format=s16le',
        f'--rate={SAMPLE_RATE}',
        f'--channels={CHANNELS}',
        '--latency-msec=20'
    ]
    return subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

class Receiver:
    def __init__(self, host, port, sink_name):
        self.host = host
        self.port = port
        self.sink_name = sink_name
        self._running = False
        self._module = None
        self._pacat = None

    def start(self):
        self._module = create_null_sink(self.sink_name)
        if self._module is None:
            sys.exit(1)
        self._running = True
        self._pacat = start_pacat(self.sink_name)
        self._serve()

    def stop(self):
        self._running = False
        if self._pacat:
            if self._pacat.stdin:
                self._pacat.stdin.close()
            self._pacat.wait()
        if self._module:
            remove_null_sink(self._module)

    def _serve(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((self.host, self.port))
        server.listen(1)
        server.settimeout(1.0)
        print(f"[tcp] Listening on {self.host}:{self.port}")

        while self._running:
            try:
                conn, addr = server.accept()
            except socket.timeout:
                continue
            print(f"[tcp] Connected from {addr}")
            self._handle(conn)
            print(f"[tcp] Disconnected")

        server.close()

    def _handle(self, conn):
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        bytes_rx = 0
        t_start = time.monotonic()
        last_report = t_start

        try:
            while self._running:
                data = conn.recv(CHUNK_BYTES)
                if not data:
                    break
                if self._pacat and self._pacat.poll() is None:
                    self._pacat.stdin.write(data)
                    self._pacat.stdin.flush()
                bytes_rx += len(data)

                t_now = time.monotonic()
                elapsed = t_now - t_start
                if t_now - last_report >= 5.0:
                    kbps = bytes_rx * 8 / elapsed / 1000 if elapsed > 0 else 0
                    print(f"[rx]  {kbps:.0f} kbps  |  {bytes_rx/1024:.0f} KB total")
                    last_report = t_now
        except (ConnectionResetError, BrokenPipeError):
            pass
        finally:
            conn.close()

def main():
    parser = argparse.ArgumentParser(description="DroidMix PC Receiver")
    parser.add_argument("--host", default="0.0.0.0", help="Bind address")
    parser.add_argument("--port", default=9000, type=int, help="TCP port")
    parser.add_argument("--device", default="PhoneMic", help="Virtual sink name")
    args = parser.parse_args()

    receiver = Receiver(args.host, args.port, args.device)

    def sig_handler(sig, frame):
        print("\n[main] Shutting down...")
        receiver.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, sig_handler)
    signal.signal(signal.SIGTERM, sig_handler)

    print("=============================")
    print("      DroidMix Receiver      ")
    print("=============================")
    receiver.start()

if __name__ == "__main__":
    main()
