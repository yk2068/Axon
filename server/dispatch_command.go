package main

func dispatchCommand(msg ClientMessage) {
	if simulator == nil {
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
		dir := "down"
		if msg.Dy > 0 {
			dir = "up"
		}
		simulator.Scroll(dir)
	case "type":
		simulator.Type(msg.Text)
	case "key":
		simulator.Key(msg.Key)
	case "keycombo":
		simulator.KeyCombo(msg.Modifier, msg.Key)
	}
}
