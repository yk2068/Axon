package main

import (
	"os/exec"
	"testing"
)

func BenchmarkADBKeepaliveLoop(b *testing.B) {
	for i := 0; i < b.N; i++ {
		_ = exec.Command("true").Run()
	}
}

func BenchmarkADBKeepaliveLoopGoroutine(b *testing.B) {
	for i := 0; i < b.N; i++ {
		go func() {
			_ = exec.Command("true").Run()
		}()
	}
}
