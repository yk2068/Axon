//go:build windows

package main

import (
	"syscall"
	"time"
	"unsafe"
)

// Windows API constants for SendInput
const (
	INPUT_MOUSE    = 0
	INPUT_KEYBOARD = 1

	// Mouse event flags
	MOUSEEVENTF_MOVE       = 0x0001
	MOUSEEVENTF_LEFTDOWN   = 0x0002
	MOUSEEVENTF_LEFTUP     = 0x0004
	MOUSEEVENTF_RIGHTDOWN  = 0x0008
	MOUSEEVENTF_RIGHTUP    = 0x0010
	MOUSEEVENTF_MIDDLEDOWN = 0x0020
	MOUSEEVENTF_MIDDLEUP   = 0x0040
	MOUSEEVENTF_WHEEL      = 0x0800

	// Keyboard event flags
	KEYEVENTF_KEYUP      = 0x0002
	KEYEVENTF_UNICODE    = 0x0004

	// Mouse wheel delta (standard Windows value)
	WHEEL_DELTA = 120

	// Virtual key codes — modifiers
	VK_SHIFT   = 0x10
	VK_CONTROL = 0x11
	VK_MENU    = 0x12 // Alt

	// Virtual key codes — navigation & editing
	VK_BACK   = 0x08
	VK_TAB    = 0x09
	VK_RETURN = 0x0D
	VK_ESCAPE = 0x1B
	VK_SPACE  = 0x20
	VK_PRIOR  = 0x21 // Page Up
	VK_NEXT   = 0x22 // Page Down
	VK_END    = 0x23
	VK_HOME   = 0x24
	VK_LEFT   = 0x25
	VK_UP     = 0x26
	VK_RIGHT  = 0x27
	VK_DOWN   = 0x28
	VK_DELETE = 0x2E

	// Virtual key codes — function keys
	VK_F1  = 0x70
	VK_F2  = 0x71
	VK_F3  = 0x72
	VK_F4  = 0x73
	VK_F5  = 0x74
	VK_F6  = 0x75
	VK_F7  = 0x76
	VK_F8  = 0x77
	VK_F9  = 0x78
	VK_F10 = 0x79
	VK_F11 = 0x7A
	VK_F12 = 0x7B

	// Virtual key codes — media & system
	VK_VOLUME_MUTE      = 0xAD
	VK_VOLUME_DOWN      = 0xAE
	VK_VOLUME_UP        = 0xAF
	VK_MEDIA_NEXT_TRACK = 0xB0
	VK_MEDIA_PREV_TRACK = 0xB1
	VK_MEDIA_PLAY_PAUSE = 0xB3
	VK_SNAPSHOT         = 0x2C
)

// specialKeyVK maps key name strings to Windows virtual key codes.
// Used by both Key() and KeyCombo() to resolve multi-character key names.
var specialKeyVK = map[string]uint16{
	"BackSpace":  VK_BACK,
	"Return":     VK_RETURN,
	"Tab":        VK_TAB,
	"Escape":     VK_ESCAPE,
	"space":      VK_SPACE,
	"Up":         VK_UP,
	"Down":       VK_DOWN,
	"Left":       VK_LEFT,
	"Right":      VK_RIGHT,
	"Home":       VK_HOME,
	"End":        VK_END,
	"Prior":      VK_PRIOR,
	"Next":       VK_NEXT,
	"Delete":     VK_DELETE,
	"F1":         VK_F1,
	"F2":         VK_F2,
	"F3":         VK_F3,
	"F4":         VK_F4,
	"F5":         VK_F5,
	"F6":         VK_F6,
	"F7":         VK_F7,
	"F8":         VK_F8,
	"F9":         VK_F9,
	"F10":        VK_F10,
	"F11":        VK_F11,
	"F12":        VK_F12,
	"volumeup":   VK_VOLUME_UP,
	"volumedown": VK_VOLUME_DOWN,
	"volumemute": VK_VOLUME_MUTE,
	"playpause":  VK_MEDIA_PLAY_PAUSE,
	"prevtrack":  VK_MEDIA_PREV_TRACK,
	"nexttrack":  VK_MEDIA_NEXT_TRACK,
	"printscreen":VK_SNAPSHOT,
}

type MouseInput struct {
	Dx          int32
	Dy          int32
	MouseData   uint32
	DwFlags     uint32
	Time        uint32
	DwExtraInfo uintptr
}
type KeyboardInput struct {
	WVk         uint16
	WScan       uint16
	DwFlags     uint32
	Time        uint32
	DwExtraInfo uintptr
}
type InputUnion struct {
	Type uint32
	Mi   MouseInput
	Pad  [8]byte
}
type InputKeyboardStruct struct {
	Type uint32
	Ki   KeyboardInput
	Pad  [8]byte
}

var (
	user32        = syscall.NewLazyDLL("user32.dll")
	procSendInput = user32.NewProc("SendInput")
)

type WindowsSimulator struct{}

func initSimulator() InputSimulator {
	return &WindowsSimulator{}
}
func (s *WindowsSimulator) sendMouseInput(dwFlags uint32, dx, dy int32, mouseData uint32) {
	var input InputUnion
	input.Type = INPUT_MOUSE
	input.Mi.DwFlags = dwFlags
	input.Mi.Dx = dx
	input.Mi.Dy = dy
	input.Mi.MouseData = mouseData
	procSendInput.Call(1, uintptr(unsafe.Pointer(&input)), uintptr(unsafe.Sizeof(input)))
}
func (s *WindowsSimulator) sendKeyboardInput(dwFlags uint32, wVk, wScan uint16) {
	var input InputKeyboardStruct
	input.Type = INPUT_KEYBOARD
	input.Ki.DwFlags = dwFlags
	input.Ki.WVk = wVk
	input.Ki.WScan = wScan
	procSendInput.Call(1, uintptr(unsafe.Pointer(&input)), uintptr(unsafe.Sizeof(input)))
}
func (s *WindowsSimulator) MoveMouse(dx, dy float64) {
	s.sendMouseInput(MOUSEEVENTF_MOVE, int32(dx), int32(dy), 0)
}
func (s *WindowsSimulator) Click(button string) {
	s.MouseDown(button)
	time.Sleep(10 * time.Millisecond)
	s.MouseUp(button)
}
func (s *WindowsSimulator) MouseDown(button string) {
	switch button {
	case "right":
		s.sendMouseInput(MOUSEEVENTF_RIGHTDOWN, 0, 0, 0)
	case "middle":
		s.sendMouseInput(MOUSEEVENTF_MIDDLEDOWN, 0, 0, 0)
	default:
		s.sendMouseInput(MOUSEEVENTF_LEFTDOWN, 0, 0, 0)
	}
}
func (s *WindowsSimulator) MouseUp(button string) {
	switch button {
	case "right":
		s.sendMouseInput(MOUSEEVENTF_RIGHTUP, 0, 0, 0)
	case "middle":
		s.sendMouseInput(MOUSEEVENTF_MIDDLEUP, 0, 0, 0)
	default:
		s.sendMouseInput(MOUSEEVENTF_LEFTUP, 0, 0, 0)
	}
}
func (s *WindowsSimulator) Scroll(direction string) {
	var data uint32 = WHEEL_DELTA
	if direction == "down" {
		// Negative WHEEL_DELTA as unsigned 32-bit: twos complement of -120
		data = 0xFFFFFF88
	}
	s.sendMouseInput(MOUSEEVENTF_WHEEL, 0, 0, data)
}
func (s *WindowsSimulator) Type(text string) {
	for _, r := range text {
		s.sendKeyboardInput(KEYEVENTF_UNICODE, 0, uint16(r))
		s.sendKeyboardInput(KEYEVENTF_UNICODE|KEYEVENTF_KEYUP, 0, uint16(r))
	}
}
func (s *WindowsSimulator) Key(key string) {
	vk, ok := specialKeyVK[key]
	if !ok {
		return
	}
	s.sendKeyboardInput(0, vk, 0)
	s.sendKeyboardInput(KEYEVENTF_KEYUP, vk, 0)
}
func (s *WindowsSimulator) KeyCombo(modifier, key string) {
	var modVk uint16
	switch modifier {
	case "ctrl":
		modVk = VK_CONTROL
	case "alt":
		modVk = VK_MENU
	case "shift":
		modVk = VK_SHIFT
	default:
		return
	}

	var keyVk uint16
	if len(key) == 1 {
		char := []rune(key)[0]
		if char >= 'a' && char <= 'z' {
			keyVk = uint16(char - 32) // Uppercase VK code
		} else {
			keyVk = uint16(char)
		}
	} else if vk, ok := specialKeyVK[key]; ok {
		keyVk = vk
	} else {
		return
	}

	s.sendKeyboardInput(0, modVk, 0)
	s.sendKeyboardInput(0, keyVk, 0)
	s.sendKeyboardInput(KEYEVENTF_KEYUP, keyVk, 0)
	s.sendKeyboardInput(KEYEVENTF_KEYUP, modVk, 0)
}
func (s *WindowsSimulator) Close() {}
