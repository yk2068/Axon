//go:build windows

package main

import (
	"sync"
	"time"
	"unsafe"
)

// Windows lets a plain user process stand in for a touchscreen. The contacts it
// feeds are delivered to applications as WM_POINTER touch input — the same
// pipeline a real touchscreen or a precision touchpad uses for a pinch — so the
// application performs a continuous zoom of its own instead of the discrete
// 100 %/110 %/125 % steps that Ctrl+wheel can only ever produce.
//
// No driver, no signing, no elevation: CreateSyntheticPointerDevice is a
// supported user-mode API. (The older InitializeTouchInjection/InjectTouchInput
// pair is rejected with ERROR_INVALID_PARAMETER on this build, so it is unused.)

type touchPoint struct{ X, Y int32 }
type touchRect struct{ Left, Top, Right, Bottom int32 }

type pointerInfo struct {
	PointerType        uint32
	PointerID          uint32
	FrameID            uint32
	PointerFlags       uint32
	SourceDevice       uintptr
	HwndTarget         uintptr
	PtPixelLocation    touchPoint
	PtHimetricLocation touchPoint
	PtPixelLocationRaw touchPoint
	PtHimetricRaw      touchPoint
	DwTime             uint32
	HistoryCount       uint32
	InputState         int32
	DwKeyStates        uint32
	PerformanceCount   uint64
	ButtonChangeType   int32
}

type pointerTouchInfo struct {
	PointerInfo  pointerInfo
	TouchFlags   uint32
	TouchMask    uint32
	RcContact    touchRect
	RcContactRaw touchRect
	Orientation  uint32
	Pressure     uint32
}

// POINTER_TYPE_INFO is a pointer type tag plus the matching info union; the
// union is pointer-aligned, hence the explicit padding word.
type pointerTypeInfo struct {
	Type      uint32
	Pad       uint32
	TouchInfo pointerTouchInfo
}

const (
	touchFeedbackNone = 0x00000003

	ptTouch = 2

	pointerFlagInRange   = 0x00000002
	pointerFlagInContact = 0x00000004
	pointerFlagDown      = 0x00010000
	pointerFlagUpdate    = 0x00020000
	pointerFlagUp        = 0x00040000

	touchMaskContactArea = 0x00000001

	smCXScreen = 0
	smCYScreen = 1

	// Finger separation used for the gesture. Wide enough that the shell sees a
	// deliberate pinch rather than a two-finger tap, narrow enough to leave room
	// to spread before the contacts hit the screen edge.
	pinchBaseSpan = 220.0
	pinchMinSpan  = 60.0
	pinchMaxSpan  = 900.0

	// One injection per this many pixels of span change. Below roughly a pixel the
	// gesture is invisible anyway, and injecting every frame just floods the shell.
	pinchStepPx = 1.0

	// The whole gesture is two contacts; asking for more only costs kernel work.
	pinchMaxContacts = 2
)

var (
	procCreateSyntheticPointerDevice  = user32.NewProc("CreateSyntheticPointerDevice")
	procDestroySyntheticPointerDevice = user32.NewProc("DestroySyntheticPointerDevice")
	procInjectSyntheticPointerInput   = user32.NewProc("InjectSyntheticPointerInput")
	procGetCursorPos                  = user32.NewProc("GetCursorPos")
	procGetSystemMetrics              = user32.NewProc("GetSystemMetrics")
)

// pinchIdleTimeout releases a gesture that stops receiving updates. The link is
// datagram-based for motion, so a dropped "end" would otherwise leave the two
// synthetic fingers down and swallow every following gesture.
const pinchIdleTimeout = 2 * time.Second

type pinchInjector struct {
	mu         sync.Mutex
	device     uintptr
	broken     bool
	warned     bool
	active     bool
	centerX    int32
	centerY    int32
	span       float64
	sentSpan   float64
	lastUpdate time.Time
}

var (
	touchPinch     = &pinchInjector{}
	pinchWatchOnce sync.Once
)

// ensureDevice creates the synthetic touch device once. A failure is permanent
// for the process, so the gesture is disabled instead of retried every frame.
func (p *pinchInjector) ensureDevice() bool {
	if p.device != 0 {
		return true
	}
	if p.broken {
		return false
	}
	dev, _, err := procCreateSyntheticPointerDevice.Call(ptTouch, pinchMaxContacts, touchFeedbackNone)
	if dev == 0 {
		p.broken = true
		logger.Printf("[TOUCH] Synthetic touch device unavailable (%v); pinch disabled\n", err)
		return false
	}
	p.device = dev
	return true
}

func (p *pinchInjector) push(flags uint32) {
	cx, cy := p.centerX, p.centerY
	half := int32(p.span / 2)
	if half < 1 {
		half = 1
	}
	scrW, _, _ := procGetSystemMetrics.Call(smCXScreen)
	scrH, _, _ := procGetSystemMetrics.Call(smCYScreen)
	w, h := int32(scrW), int32(scrH)
	// Keep both contacts on screen: a contact clamped to the edge would slide the
	// gesture centre under the user's finger and the zoom would drift.
	if cx-half < 4 {
		cx = half + 4
	}
	if cx+half > w-4 {
		cx = w - 4 - half
	}
	if cy < 4 {
		cy = 4
	}
	if cy > h-4 {
		cy = h - 4
	}

	var infos [pinchMaxContacts]pointerTypeInfo
	for i := range infos {
		x := cx - half + int32(i)*half*2
		infos[i].Type = ptTouch
		infos[i].TouchInfo.PointerInfo.PointerType = ptTouch
		infos[i].TouchInfo.PointerInfo.PointerID = uint32(i)
		infos[i].TouchInfo.PointerInfo.PointerFlags = flags
		infos[i].TouchInfo.PointerInfo.PtPixelLocation = touchPoint{x, cy}
		infos[i].TouchInfo.PointerInfo.PtPixelLocationRaw = touchPoint{x, cy}
		// Contact area only. Pressure and orientation are optional hints the shell
		// does not need for a gesture, and leaving them out keeps the frame minimal.
		infos[i].TouchInfo.TouchMask = touchMaskContactArea
		infos[i].TouchInfo.RcContact = touchRect{x - 2, cy - 2, x + 2, cy + 2}
		infos[i].TouchInfo.RcContactRaw = infos[i].TouchInfo.RcContact
	}
	ok, _, err := procInjectSyntheticPointerInput.Call(
		p.device,
		uintptr(unsafe.Pointer(&infos[0])),
		pinchMaxContacts,
	)
	if ok == 0 && !p.warned {
		// One warning is enough: a rejected injection repeats on every frame.
		p.warned = true
		logger.Printf("[TOUCH] InjectSyntheticPointerInput rejected: %v\n", err)
	}
}

// pinchBegin anchors the gesture on the current cursor position, so the zoom
// applies to whatever the pointer is over — the same rule a touchpad follows.
// A begin that arrives while a gesture is still open (a lost "end") replaces it
// rather than being ignored, which keeps the gesture usable after packet loss.
func pinchBegin() {
	p := touchPinch
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.ensureDevice() {
		return
	}
	if p.active {
		p.push(pointerFlagInRange | pointerFlagUp)
		p.active = false
	}
	var pt touchPoint
	procGetCursorPos.Call(uintptr(unsafe.Pointer(&pt)))
	p.centerX = pt.X
	p.centerY = pt.Y
	p.span = pinchBaseSpan
	p.sentSpan = pinchBaseSpan
	p.active = true
	p.lastUpdate = time.Now()
	p.push(pointerFlagInRange | pointerFlagInContact | pointerFlagDown)
	p.armWatchdog()
}

func (p *pinchInjector) armWatchdog() {
	pinchWatchOnce.Do(func() {
		go func() {
			ticker := time.NewTicker(200 * time.Millisecond)
			defer ticker.Stop()
			for range ticker.C {
				touchPinch.expireIdle()
			}
		}()
	})
}

func (p *pinchInjector) expireIdle() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.active || time.Since(p.lastUpdate) < pinchIdleTimeout {
		return
	}
	p.push(pointerFlagInRange | pointerFlagUp)
	p.active = false
}

// pinchUpdate applies a relative change to the finger separation: 0.05 means
// "fingers 5 % further apart than last frame".
func pinchUpdate(delta float64) {
	p := touchPinch
	if delta == 0 {
		return
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.active {
		return
	}
	p.lastUpdate = time.Now()
	p.span *= 1 + delta
	if p.span < pinchMinSpan {
		p.span = pinchMinSpan
	}
	if p.span > pinchMaxSpan {
		p.span = pinchMaxSpan
	}
	if diff := p.span - p.sentSpan; diff < pinchStepPx && diff > -pinchStepPx {
		return
	}
	p.sentSpan = p.span
	p.push(pointerFlagInRange | pointerFlagInContact | pointerFlagUpdate)
}

func pinchEnd() {
	p := touchPinch
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.active {
		return
	}
	p.push(pointerFlagInRange | pointerFlagUp)
	p.active = false
}
