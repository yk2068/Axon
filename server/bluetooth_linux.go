//go:build linux

package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	"os"
)

func startBluetoothServer() {
	fd, err := unix.Socket(unix.AF_BLUETOOTH, unix.SOCK_STREAM, unix.BTPROTO_RFCOMM)
	if err != nil {
		fmt.Println("Error creating Bluetooth socket:", err)
		return
	}
	defer unix.Close(fd)
	addr := &unix.SockaddrRFCOMM{
		Channel: 1,
		Addr:    [6]uint8{0, 0, 0, 0, 0, 0},
	}
	if err := unix.Bind(fd, addr); err != nil {
		fmt.Println("Error binding to Bluetooth (maybe another service is using channel 1):", err)
		return
	}
	if err := unix.Listen(fd, 1); err != nil {
		fmt.Println("Error listening on Bluetooth:", err)
		return
	}
	fmt.Println("RFCOMM Bluetooth server listening on channel 1")
	for {
		clientFd, sa, err := unix.Accept(fd)
		if err != nil {
			fmt.Println("Error accepting Bluetooth connection:", err)
			continue
		}
		if btAddr, ok := sa.(*unix.SockaddrRFCOMM); ok {
			fmt.Printf("[CONNECTION] Bluetooth client connected from MAC: %02X:%02X:%02X:%02X:%02X:%02X\n",
				btAddr.Addr[5], btAddr.Addr[4], btAddr.Addr[3], btAddr.Addr[2], btAddr.Addr[1], btAddr.Addr[0])
		}
		go handleBluetoothConnection(clientFd)
	}
}
func handleBluetoothConnection(fd int) {
	file := os.NewFile(uintptr(fd), "rfcomm")
	defer file.Close()
	scanner := bufio.NewScanner(file)

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
		fmt.Println("[DISCONNECTION] Bluetooth client error:", err)
	} else {
		fmt.Println("[DISCONNECTION] Bluetooth client disconnected")
	}
}
func processBluetoothData(data []byte) {
	var msg ClientMessage
	if err := json.Unmarshal(data, &msg); err != nil {
		return
	}
	dispatchCommand(msg)
}
