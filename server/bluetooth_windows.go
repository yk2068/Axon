//go:build windows

package main

import (
	"bufio"
	"encoding/json"

	"net"
	"sync"
	"time"
)

var (
	bluetoothListenerWindows net.Listener
	bluetoothConnMutex       sync.Mutex
	bluetoothConnections     = make(map[net.Conn]bool)
)

func startBluetoothServer() {
	logger.Println("[BT] Starting native RFCOMM server on Windows")
	go startNativeRFCOMMServer()
}
func startNativeRFCOMMServer() {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		logger.Printf("[BT] Error creating RFCOMM listener: %v\n", err)
		return
	}
	defer listener.Close()
	bluetoothListenerWindows = listener
	logger.Printf("[BT] RFCOMM server listening on %s (Windows native)\n", listener.Addr().String())
	for {
		conn, err := listener.Accept()
		if err != nil {
			logger.Printf("[BT] Error accepting connection: %v\n", err)
			continue
		}
		if tcpConn, ok := conn.(*net.TCPConn); ok {
			remoteAddr := tcpConn.RemoteAddr().String()
			logger.Printf("[CONNECTION] Bluetooth client connected from: %s\n", remoteAddr)
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
		logger.Println("[DISCONNECTION] Bluetooth client disconnected")
	}()

	scanner := bufio.NewScanner(conn)
	// Increase scanner buffer size in case of large messages
	buf := make([]byte, 0, 64*1024)
	scanner.Buffer(buf, 1024*1024)

	for scanner.Scan() {
		processBluetoothData(scanner.Bytes())
	}

	if err := scanner.Err(); err != nil {
		logger.Printf("[BT] Read error: %v\n", err)
	}
}
func processBluetoothData(data []byte) {
	text := string(data)
	var msg ClientMessage
	err := json.Unmarshal([]byte(text), &msg)
	if err != nil {
		return
	}
	switch msg.Type {
	case "move":
		simulator.MoveMouse(msg.Dx, msg.Dy)
	case "click":
		simulator.Click(msg.Button)
	case "mousedown":
		simulator.MouseDown(msg.Button)
	case "mouseup":
		simulator.MouseUp(msg.Button)
	case "scroll":
		simulator.ScrollDelta(msg.Dy)
	case "type":
		logger.Printf("[BT KEYBOARD] Typing text: %q\n", msg.Text)
		simulator.Type(msg.Text)
	case "key":
		logger.Printf("[BT KEYBOARD] Pressing special key: %q\n", msg.Key)
		simulator.Key(msg.Key)
	case "keycombo":
		logger.Printf("[BT KEYBOARD] Pressing combo: %s+%s\n", msg.Modifier, msg.Key)
		simulator.KeyCombo(msg.Modifier, msg.Key)
	}
}
