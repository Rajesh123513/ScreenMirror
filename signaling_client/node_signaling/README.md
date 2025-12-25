Local signaling server for testing

1) Install dependencies:

```bash
cd screen_mirror/signaling_client/node_signaling
npm install
```

2) Start server:

```bash
npm start
```

3) Open the browser client (http://localhost:8081) and enter `ws://localhost:9000` as the signaling URL. This will allow you to verify the browser side of the flow without the TV app.

Diagnostics commands

- Check WebSocket from terminal using `wscat` (install via `npm i -g wscat`):

```bash
wscat -c ws://10.0.2.16:9000
```

- Test TCP port reachability (macOS):

```bash
nc -vz 10.0.2.16 9000
```

- Ping the TV:

```bash
ping -c 4 10.0.2.16
```

- Check browser console (press F12) for WebSocket errors when clicking Start on the web client.

- If you have ADB access to the TV/device, collect logs for the signaling server:

```bash
adb logcat -s SignalingServer MainActivity WebRTCClient
```

If the browser connects to this local server but not to the TV, the issue is network/reachability or the TV app isn't running/started correctly. If the browser fails to connect to this local server, the client.js needs debugging (open browser console and report errors).