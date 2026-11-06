/**
 * Ozone Player — Together Online Relay Server (Node.js)
 * Standalone relay server using native http + ws.
 * Can be run locally in Termux, on a VPS, or deployed to Render / Railway / Fly.io.
 */

const http = require("http");
const crypto = require("crypto");
const { WebSocketServer, WebSocket } = require("ws");

const PORT = process.env.PORT || 8787;

const rooms = new Map(); // sessionId -> room
const codeToSession = new Map(); // code -> sessionId

function generateRoomCode() {
  for (let i = 0; i < 100; i++) {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    if (!codeToSession.has(code)) return code;
  }
  return Math.random().toString(36).substring(2, 8).toUpperCase();
}

function sendJson(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function broadcastToRoom(room, message, excludeParticipantId = null) {
  for (const [id, p] of room.participants) {
    if (id !== excludeParticipantId && p.ws.readyState === WebSocket.OPEN) {
      sendJson(p.ws, message);
    }
  }
}

function cleanupParticipant(room, participantId, reason) {
  const p = room.participants.get(participantId);
  if (!p) return;

  room.participants.delete(participantId);

  if (p.isHost) {
    broadcastToRoom(room, {
      type: "server_error",
      sessionId: room.sessionId,
      message: "Host closed the session",
    });
    for (const [, guest] of room.participants) {
      guest.ws.close(1000, "Host closed session");
    }
    room.participants.clear();
    rooms.delete(room.sessionId);
    codeToSession.delete(room.code);
  } else {
    broadcastToRoom(room, {
      type: "participant_left",
      sessionId: room.sessionId,
      participantId,
      reason,
    });
  }
}

// HTTP Server
const server = http.createServer((req, res) => {
  // CORS
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const pathname = parsedUrl.pathname.replace(/^\/v1/, "");

  if (pathname === "/" || pathname === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({
      ok: true,
      service: "Ozone Together Online Relay Server (Node.js)",
      activeRooms: rooms.size,
    }));
    return;
  }

  let body = "";
  req.on("data", (chunk) => { body += chunk; });
  req.on("end", () => {
    let parsedBody = {};
    try { if (body) parsedBody = JSON.parse(body); } catch (_) {}

    // POST /v1/together/sessions
    if (req.method === "POST" && pathname === "/together/sessions") {
      const hostDisplayName = (parsedBody.hostDisplayName || "Host").trim();
      const settings = parsedBody.settings || {
        allowGuestsToAddTracks: true,
        allowGuestsToControlPlayback: false,
        requireHostApprovalToJoin: false,
      };

      const sessionId = crypto.randomUUID();
      const code = generateRoomCode();
      const hostKey = crypto.randomUUID().replace(/-/g, "");
      const guestKey = crypto.randomUUID().replace(/-/g, "");

      const host = req.headers.host || `localhost:${PORT}`;
      const isSecure = req.headers["x-forwarded-proto"] === "https";
      const wsProtocol = isSecure ? "wss:" : "ws:";
      const wsUrl = `${wsProtocol}//${host}/v1/together/ws`;

      const room = {
        sessionId,
        code,
        hostKey,
        guestKey,
        settings,
        hostDisplayName,
        createdAt: Date.now(),
        hostWs: null,
        participants: new Map(),
      };

      rooms.set(sessionId, room);
      codeToSession.set(code, sessionId);

      setTimeout(() => {
        if (rooms.has(sessionId)) {
          rooms.delete(sessionId);
          codeToSession.delete(code);
        }
      }, 24 * 60 * 60 * 1000);

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({
        sessionId,
        code,
        hostKey,
        guestKey,
        wsUrl,
        settings,
      }));
      return;
    }

    // POST /v1/together/sessions/resolve
    if (req.method === "POST" && pathname === "/together/sessions/resolve") {
      const code = (parsedBody.code || "").trim();
      const sessionId = codeToSession.get(code);

      if (!sessionId || !rooms.has(sessionId)) {
        res.writeHead(404, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: false, error: "Session not found or expired" }));
        return;
      }

      const room = rooms.get(sessionId);
      const host = req.headers.host || `localhost:${PORT}`;
      const isSecure = req.headers["x-forwarded-proto"] === "https";
      const wsProtocol = isSecure ? "wss:" : "ws:";
      const wsUrl = `${wsProtocol}//${host}/v1/together/ws`;

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({
        sessionId: room.sessionId,
        guestKey: room.guestKey,
        wsUrl,
        settings: room.settings,
      }));
      return;
    }

    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: false, error: "Not Found" }));
  });
});

// WebSocket Server
const wss = new WebSocketServer({ noServer: true });

server.on("upgrade", (req, socket, head) => {
  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit("connection", ws, req);
  });
});

wss.on("connection", (ws) => {
  let boundSessionId = null;
  let boundParticipantId = null;
  let isHostRole = false;

  ws.on("message", (raw) => {
    try {
      const data = JSON.parse(raw.toString());
      const type = data.type;

      if (type === "client_hello") {
        const { sessionId, sessionKey, displayName } = data;
        const room = rooms.get(sessionId);

        if (!room) {
          sendJson(ws, {
            type: "server_error",
            sessionId,
            message: "Room not found or expired",
            code: "ROOM_NOT_FOUND",
          });
          ws.close(1008, "Room not found");
          return;
        }

        boundSessionId = sessionId;
        const participantId = crypto.randomUUID();
        boundParticipantId = participantId;

        if (sessionKey === room.hostKey) {
          isHostRole = true;
          room.hostWs = ws;
          room.participants.set(participantId, {
            ws,
            name: displayName || room.hostDisplayName,
            isHost: true,
            isPending: false,
          });

          sendJson(ws, {
            type: "server_welcome",
            protocolVersion: 1,
            sessionId,
            participantId,
            role: "HOST",
            isPending: false,
            settings: room.settings,
          });
        } else if (sessionKey === room.guestKey) {
          isHostRole = false;
          const requireApproval = room.settings?.requireHostApprovalToJoin ?? false;

          room.participants.set(participantId, {
            ws,
            name: displayName || "Guest",
            isHost: false,
            isPending: requireApproval,
          });

          sendJson(ws, {
            type: "server_welcome",
            protocolVersion: 1,
            sessionId,
            participantId,
            role: "GUEST",
            isPending: requireApproval,
            settings: room.settings,
          });

          const participantObj = {
            id: participantId,
            name: displayName || "Guest",
            isHost: false,
            isPending: requireApproval,
            isConnected: true,
          };

          if (requireApproval) {
            if (room.hostWs) {
              sendJson(room.hostWs, {
                type: "join_request",
                sessionId,
                participant: participantObj,
              });
            }
          } else {
            broadcastToRoom(room, {
              type: "participant_joined",
              sessionId,
              participant: participantObj,
            });
          }
        } else {
          sendJson(ws, {
            type: "server_error",
            sessionId,
            message: "Invalid session key",
            code: "UNAUTHORIZED",
          });
          ws.close(1008, "Unauthorized");
        }
        return;
      }

      if (type === "heartbeat_ping") {
        sendJson(ws, {
          type: "heartbeat_pong",
          sessionId: data.sessionId,
          pingId: data.pingId,
          clientElapsedRealtimeMs: data.clientElapsedRealtimeMs,
          serverElapsedRealtimeMs: Date.now(),
        });
        return;
      }

      const room = rooms.get(boundSessionId);
      if (!room) return;

      if (type === "room_state") {
        for (const [id, p] of room.participants) {
          if (id !== boundParticipantId && p.ws.readyState === WebSocket.OPEN) {
            sendJson(p.ws, data);
          }
        }
        return;
      }

      if (type === "join_decision") {
        const { participantId, approved } = data;
        const target = room.participants.get(participantId);
        if (target) {
          target.isPending = false;
          if (approved) {
            broadcastToRoom(room, {
              type: "participant_joined",
              sessionId: room.sessionId,
              participant: {
                id: participantId,
                name: target.name,
                isHost: false,
                isPending: false,
                isConnected: true,
              },
            });
          } else {
            sendJson(target.ws, {
              type: "server_error",
              sessionId: room.sessionId,
              message: "Host declined join request",
            });
            target.ws.close(1000, "Declined");
            room.participants.delete(participantId);
          }
        }
        return;
      }

      if (type === "control_request" || type === "add_track_request") {
        if (room.hostWs && room.hostWs.readyState === WebSocket.OPEN) {
          sendJson(room.hostWs, data);
        }
        return;
      }

      if (type === "kick" || type === "ban") {
        const target = room.participants.get(data.participantId);
        if (target) {
          sendJson(target.ws, {
            type: "server_error",
            sessionId: room.sessionId,
            message: data.reason || "Removed by host",
          });
          target.ws.close(1000, "Kicked");
          room.participants.delete(data.participantId);
          broadcastToRoom(room, {
            type: "participant_left",
            sessionId: room.sessionId,
            participantId: data.participantId,
            reason: data.reason || "Kicked",
          });
        }
        return;
      }

      if (type === "host_transfer") {
        broadcastToRoom(room, {
          type: "host_transferred",
          sessionId: room.sessionId,
          participantId: data.participantId,
        });
        return;
      }

      if (type === "client_leave") {
        cleanupParticipant(room, boundParticipantId, "Left voluntarily");
        ws.close(1000, "Left");
      }
    } catch (_) {}
  });

  ws.on("close", () => {
    if (boundSessionId && boundParticipantId) {
      const room = rooms.get(boundSessionId);
      if (room) {
        cleanupParticipant(room, boundParticipantId, isHostRole ? "Host disconnected" : "Disconnected");
      }
    }
  });
});

server.listen(PORT, () => {
  console.log(`Ozone Together Relay Server listening on port ${PORT}`);
});
