const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 9000 });

console.log('Local signaling server listening on ws://localhost:9000');

wss.on('connection', function connection(ws, req) {
  console.log('Client connected from', req.socket.remoteAddress);
  ws.on('message', function incoming(message) {
    console.log('received: %s', message);
    // Echo offers/answers/candidates to all clients (simple relay)
    try {
      const obj = JSON.parse(message);
      if (obj.type) {
        // broadcast to others
        wss.clients.forEach(function each(client) {
          if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(obj));
          }
        });
      }
    } catch (e) {
      console.log('non-json message:', message.toString());
    }
  });

  ws.on('close', () => {
    console.log('Client disconnected');
  });
});
