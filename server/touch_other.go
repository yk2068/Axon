//go:build !windows

package main

// Pinch-to-zoom is only wired up for the Windows touch injection API; on the
// other platforms the gesture is accepted on the wire and then dropped.
func pinchBegin()           {}
func pinchUpdate(d float64) {}
func pinchEnd()             {}
