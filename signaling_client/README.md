Quick start - Screen Mirror Sender

1) Serve the `signaling_client` folder on a local web server (or open `index.html` in a browser that allows getDisplayMedia):

Mac (Python 3):

```bash
cd screen_mirror/signaling_client
python3 -m http.server 8080
```

2) Open the page in Chrome/Edge: http://localhost:8080

3) In the input provide the TV WebSocket URL shown in the Android app (e.g. `ws://10.0.0.53:9000`) and click Start.

4) Allow screen sharing when prompted. The browser will capture the screen and send a WebRTC offer to the TV.

Notes:
- The Android app runs a WebSocket signaling server on port 9000 by default; ensure the TV and the sender are on the same network.
- This is a minimal prototype. For stable 4K streaming, use Chrome/Edge, ensure sufficient network bandwidth, and consider adding TURN servers for NAT traversal (not implemented here).