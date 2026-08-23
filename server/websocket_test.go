package main

import (
	"github.com/gorilla/websocket"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"strings"
	"testing"
)

func TestWebSocketOrigin(t *testing.T) {
	activeToken = "test_token"
	server := httptest.NewServer(http.HandlerFunc(handleWebSocket))
	
	// Parse the randomly assigned port and update the global port variable
	// so the CheckOrigin function allows it.
	_, portStr, err := net.SplitHostPort(strings.TrimPrefix(server.URL, "http://"))
	if err == nil {
		p, _ := strconv.Atoi(portStr)
		port = p
	}
	defer server.Close()
	wsURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/?token=test_token"
	dialer := websocket.Dialer{}
	headers := http.Header{}
	headers.Add("Origin", "http://evil.com")
	_, resp, err := dialer.Dial(wsURL, headers)
	if err == nil {
		t.Fatalf("Expected connection to fail with malicious Origin, but it succeeded")
	}
	if resp != nil && resp.StatusCode != http.StatusForbidden {
		t.Errorf("Expected 403 Forbidden, got %d", resp.StatusCode)
	}
	dialer2 := websocket.Dialer{}
	conn, resp2, err2 := dialer2.Dial(wsURL, nil)
	if err2 != nil {
		t.Fatalf("Expected connection without Origin to succeed, got error: %v", err2)
	}
	if resp2 != nil && resp2.StatusCode != http.StatusSwitchingProtocols {
		t.Errorf("Expected 101 Switching Protocols, got %d", resp2.StatusCode)
	}
	conn.Close()
	dialer3 := websocket.Dialer{}
	headers3 := http.Header{}

	// Temporarily mock the port for the test to match the httptest server's port
	u, _ := url.Parse(server.URL)
	testPort, _ := strconv.Atoi(u.Port())
	originalPort := port
	port = testPort
	defer func() { port = originalPort }()

	headers3.Add("Origin", server.URL)
	conn3, resp3, err3 := dialer3.Dial(wsURL, headers3)
	if err3 != nil {
		t.Fatalf("Expected connection with matching Origin to succeed, got error: %v", err3)
	}
	if resp3 != nil && resp3.StatusCode != http.StatusSwitchingProtocols {
		t.Errorf("Expected 101 Switching Protocols, got %d", resp3.StatusCode)
	}
	conn3.Close()
}
