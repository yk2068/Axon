//go:build darwin

package main

import (
	"bufio"
	"encoding/json"
	"fmt"

	"net"
	"sync"
	"time"
)

var (
	bluetoothListenerDarwin net.Listener
	bluetoothConnMutex      sync.Mutex
	bluetoothConnections    = make(map[net.Conn]bool)
)

func startBluetoothServer() {
	fmt.Println("[BT] Starting native RFCOMM server on macOS")
	go startNativeRFCOMMServer()
}
func startNativeRFCOMMServer() {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Printf("[BT] Error creating RFCOMM listener: %v\n", err)
		return
	}
	defer listener.Close()
	bluetoothListenerDarwin = listener
	fmt.Printf("[BT] RFCOMM server listening on %s (macOS native)\n", listener.Addr().String())
	for {
		conn, err := listener.Accept()
		if err != nil {
			fmt.Printf("[BT] Error accepting connection: %v\n", err)
			continue
		}
		if tcpConn, ok := conn.(*net.TCPConn); ok {
			remoteAddr := tcpConn.RemoteAddr().String()
			fmt.Printf("[CONNECTION] Bluetooth client connected from: %s\n", remoteAddr)
			tcpConn.SetNoDelay(true)
			tcpConn.SetKeepAlive(true)
			tcpConn.SetKeepAlivePeriod(1 * time.Second)
			tcpConn.SetReadBuffer(65536)
			tcpConn.SetWriteBuffer(65536)
		}
		bluetoothConnMutex.Lock()
		bluetoothConnections[conn] = true
		bluetoothConnMutex.Unlock()
		go handleBluetoothConnection(conn)
	}
}
func handleBluetoothConnection(conn net.Conn) {
	defer func() {
		conn.Close()
		bluetoothConnMutex.Lock()
		delete(bluetoothConnections, conn)
		bluetoothConnMutex.Unlock()
		fmt.Println("[DISCONNECTION] Bluetooth client disconnected")
	}()

	scanner := bufio.NewScanner(conn)
	buf := make([]byte, 0, 64*1024)
	scanner.Buffer(buf, 1024*1024)

	// First message must be an auth token
	if !scanner.Scan() {
		fmt.Println("[BT] Client disconnected before authentication")
		return
	}
	var authMsg ClientMessage
	if err := json.Unmarshal(scanner.Bytes(), &authMsg); err != nil || authMsg.Type != "auth" || authMsg.Token != activeToken {
		fmt.Println("[SECURITY] Bluetooth client failed authentication")
		return
	}
	fmt.Println("[BT] Client authenticated successfully")

	for scanner.Scan() {
		processBluetoothData(scanner.Bytes())
	}

	if err := scanner.Err(); err != nil {
		fmt.Printf("[BT] Read error: %v\n", err)
	}
}
func processBluetoothData(data []byte) {
	var msg ClientMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		return
	}
	dispatchCommand(msg)
}
