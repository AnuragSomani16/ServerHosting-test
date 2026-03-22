const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const { WebSocketServer } = require('ws');
const path = require('path');

const PROTO_PATH = path.join(__dirname, '..', 'proto', 'communication.proto');

const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
});

const proto = grpc.loadPackageDefinition(packageDefinition).communication;

const wss = new WebSocketServer({ port: 3001 });
const wsClients = new Set();

wss.on('connection', (ws) => {
    console.log('[Server A] App 1 connected via WebSocket');
    wsClients.add(ws);

    ws.on('message', (data) => {
        const message = JSON.parse(data);
        console.log('[Server A] Received from App 1:', message);

        // Relay to Server B via gRPC
        grpcClient.SendMessage({
            sender: 'server-a',
            content: message.content,
            timestamp: message.sentAt || new Date().toISOString(),
        }, (err, response) => {
            if (err) console.error('[Server A] gRPC error:', err);
            else console.log('[Server A] Server B replied:', response);
        });
    });

    ws.on('close', () => wsClients.delete(ws));
});

const grpcServer = new grpc.Server();

grpcServer.addService(proto.ChatService.service, {
    SendMessage: (call, callback) => {
        const msg = call.request;
        console.log('[Server A] Received via gRPC from Server B:', msg);

        // Push to all connected WebSocket clients (App 1)
        wsClients.forEach((ws) => {
            ws.send(JSON.stringify({ from: msg.sender, content: msg.content, sentAt: msg.timestamp }));
        });

        callback(null, { success: true });
    },
});

grpcServer.bindAsync('0.0.0.0:50051', grpc.ServerCredentials.createInsecure(), () => {
    console.log('[Server A] gRPC server running on port 50051');
});

const grpcClient = new proto.ChatService(
    'localhost:50052',
    grpc.credentials.createInsecure()
);
console.log('[Server A] WebSocket server running on port 3001');