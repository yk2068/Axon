package main

import "fmt"

// dispatchCommand routes a parsed client message to the appropriate simulator action.
// This is the single source of truth for command handling, used by WebSocket,
// Bluetooth, and any future transport layer.
func dispatchCommand(msg ClientMessage) {
	switch msg.Type {
	case "pong":
		// Handled at the transport level (WebSocket only), not here.
	case "move":
		simulator.MoveMouse(msg.Dx, msg.Dy)
	case "click":
		simulator.Click(msg.Button)
	case "mousedown":
		simulator.MouseDown(msg.Button)
	case "mouseup":
		simulator.MouseUp(msg.Button)
	case "scroll":
		dir := "down"
		if msg.Dy > 0 {
			dir = "up"
		}
		simulator.Scroll(dir)
	case "type":
		fmt.Printf("[KEYBOARD] Typing text: %q\n", msg.Text)
		simulator.Type(msg.Text)
	case "key":
		fmt.Printf("[KEYBOARD] Pressing special key: %q\n", msg.Key)
		simulator.Key(msg.Key)
	case "keycombo":
		fmt.Printf("[KEYBOARD] Pressing combo: %s+%s\n", msg.Modifier, msg.Key)
		simulator.KeyCombo(msg.Modifier, msg.Key)
	}
}
