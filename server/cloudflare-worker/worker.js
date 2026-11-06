/**
 * Ozone Player — Together Online Relay Server
 * Cloudflare Worker with WebSocket Relay & Session Management
 * 
 * Free tier compatible (100,000 requests/day).
 * Deploy via Cloudflare Dashboard (Quick Edit) or Wrangler CLI (`npx wrangler deploy`).
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // 1. CORS Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    // 2. Health check
    if (url.pathname === "/" || url.pathname === "/health") {
      return jsonResponse({
        ok: true,
        service: "Ozone Together Online Relay Server",
        version: "1.0.0",
        activeRooms: globalRooms.size,
      });
    }

    // 3. API v1 Router
    const pathname = url.pathname.replace(/^\/v1/, "");

    // 3a. Create Session: POST /v1/together/sessions
    if (request.method === "POST" && pathname === "/together/sessions") {
      return handleCreateSession(request, url);
    }

    // 3b. Resolve Code: POST /v1/together/sessions/resolve
    if (request.method === "POST" && pathname === "/together/sessions/resolve") {
      return handleResolveSession(request);
    }

    // 3c. WebSocket Connection: GET /v1/together/ws or /together
    if (request.headers.get("Upgrade") === "websocket" || pathname === "/together/ws" || pathname === "/together") {
      return handleWebSocketUpgrade(request, url);
    }

    return jsonResponse({ ok: false, error: "Not Found" }, 404);
  },
};

// Global in-memory storage for active rooms
// Room structure:
// {
//   sessionId: string,
//   code: string,
//   hostKey: string,
//   guestKey: string,
//   settings: object,
//   hostDisplayName: string,
//   createdAt: number,
//   hostWs: WebSocket | null,
//   participants: Map<string, { ws: WebSocket, name: string, isHost: boolean, isPending: boolean }>
// }
const globalRooms = new Map(); // sessionId -> room
const codeToSession = new Map(); // code -> sessionId

// Helper: 6-digit room code generator
function generateRoomCode() {
  for (let i = 0; i < 100; i++) {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    if (!codeToSession.has(code)) return code;
  }
  return Math.random().toString(36).substring(2, 8).toUpperCase();
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

// Handler: Create Session
async function handleCreateSession(request, url) {
  try {
    const body = await request.json().catch(() => ({}));
    const hostDisplayName = (body.hostDisplayName || "Host").trim();
    const settings = body.settings || {
      allowGuestsToAddTracks: true,
      allowGuestsToControlPlayback: false,
      requireHostApprovalToJoin: false,
    };

    const sessionId = crypto.randomUUID();
    const code = generateRoomCode();
    const hostKey = crypto.randomUUID().replace(/-/g, "");
    const guestKey = crypto.randomUUID().replace(/-/g, "");

    const wsProtocol = url.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${wsProtocol}//${url.host}/v1/together/ws`;

    const room = {
      sessionId,
      code,
      hostKey,
      guestKey,
      settings,
      hostDisplayName,
      createdAt: Date.now(),
      hostWs: null,
      participants: new Map(), // participantId -> { ws, name, isHost, isPending }
    };

    globalRooms.set(sessionId, room);
    codeToSession.set(code, sessionId);

    // Auto cleanup stale rooms after 24 hours
    setTimeout(() => {
      if (globalRooms.has(sessionId)) {
        globalRooms.delete(sessionId);
        codeToSession.delete(code);
      }
    }, 24 * 60 * 60 * 1000);

    return jsonResponse({
      sessionId,
      code,
      hostKey,
      guestKey,
      wsUrl,
      settings,
    });
  } catch (e) {
    return jsonResponse({ ok: false, error: e.message || "Failed to create session" }, 500);
  }
}

// Handler: Resolve Session by 6-digit code
async function handleResolveSession(request) {
  try {
    const body = await request.json().catch(() => ({}));
    const code = (body.code || "").trim();

    const sessionId = codeToSession.get(code);
    if (!sessionId || !globalRooms.has(sessionId)) {
      return jsonResponse({ ok: false, error: "Session not found or expired" }, 404);
    }

    const room = globalRooms.get(sessionId);
    const host = request.headers.get("Host") || "ozone-together.workers.dev";
    const wsUrl = `wss://${host}/v1/together/ws`;

    return jsonResponse({
      sessionId: room.sessionId,
      guestKey: room.guestKey,
      wsUrl,
      settings: room.settings,
    });
  } catch (e) {
    return jsonResponse({ ok: false, error: e.message || "Failed to resolve code" }, 500);
  }
}

// Handler: WebSocket Relay Upgrade
function handleWebSocketUpgrade(request, url) {
  const webSocketPair = new WebSocketPair();
  const [client, server] = Object.values(webSocketPair);

  server.accept();

  let boundSessionId = null;
  let boundParticipantId = null;
  let isHostRole = false;

  server.addEventListener("message", (event) => {
    try {
      const data = JSON.parse(event.data);
      const type = data.type;

      // ─── 1. Client Hello (Auth & Role Binding) ───────────────────────────
      if (type === "client_hello") {
        const { sessionId, sessionKey, displayName } = data;
        const room = globalRooms.get(sessionId);

        if (!room) {
          sendJson(server, {
            type: "server_error",
            sessionId,
            message: "Room not found or expired",
            code: "ROOM_NOT_FOUND",
          });
          server.close(1008, "Room not found");
          return;
        }

        boundSessionId = sessionId;
        const participantId = crypto.randomUUID();
        boundParticipantId = participantId;

        if (sessionKey === room.hostKey) {
          // Host connection
          isHostRole = true;
          room.hostWs = server;
          room.participants.set(participantId, {
            ws: server,
            name: displayName || room.hostDisplayName,
            isHost: true,
            isPending: false,
          });

          sendJson(server, {
            type: "server_welcome",
            protocolVersion: 1,
            sessionId,
            participantId,
            role: "HOST",
            isPending: false,
            settings: room.settings,
          });
        } else if (sessionKey === room.guestKey) {
          // Guest connection
          isHostRole = false;
          const requireApproval = room.settings?.requireHostApprovalToJoin ?? false;

          room.participants.set(participantId, {
            ws: server,
            name: displayName || "Guest",
            isHost: false,
            isPending: requireApproval,
          });

          sendJson(server, {
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
            // Notify Host for approval
            if (room.hostWs) {
              sendJson(room.hostWs, {
                type: "join_request",
                sessionId,
                participant: participantObj,
              });
            }
          } else {
            // Broadcast guest joined to all room participants
            broadcastToRoom(room, {
              type: "participant_joined",
              sessionId,
              participant: participantObj,
            });
          }
        } else {
          sendJson(server, {
            type: "server_error",
            sessionId,
            message: "Invalid session key",
            code: "UNAUTHORIZED",
          });
          server.close(1008, "Unauthorized");
        }
        return;
      }

      // ─── 2. Heartbeat Ping / Pong (Sub-millisecond Clock Sync) ───────────
      if (type === "heartbeat_ping") {
        sendJson(server, {
          type: "heartbeat_pong",
          sessionId: data.sessionId,
          pingId: data.pingId,
          clientElapsedRealtimeMs: data.clientElapsedRealtimeMs,
          serverElapsedRealtimeMs: Date.now(),
        });
        return;
      }

      const room = globalRooms.get(boundSessionId);
      if (!room) return;

      // ─── 3. Host State Broadcasts ─────────────────────────────────────────
      if (type === "room_state") {
        // Relay room playback state to all guests
        for (const [id, p] of room.participants) {
          if (id !== boundParticipantId && p.ws.readyState === WebSocket.OPEN) {
            sendJson(p.ws, data);
          }
        }
        return;
      }

      // ─── 4. Join Decisions from Host ──────────────────────────────────────
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

      // ─── 5. Guest Control & Queue Requests -> Forward to Host ─────────────
      if (type === "control_request" || type === "add_track_request") {
        if (room.hostWs && room.hostWs.readyState === WebSocket.OPEN) {
          sendJson(room.hostWs, data);
        }
        return;
      }

      // ─── 6. Kick / Ban / Host Transfer ────────────────────────────────────
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

      // ─── 7. Client Leave ──────────────────────────────────────────────────
      if (type === "client_leave") {
        cleanupParticipant(room, boundParticipantId, "Left voluntarily");
        server.close(1000, "Left");
      }
    } catch (e) {
      // Ignore parse errors from malformed frames
    }
  });

  server.addEventListener("close", () => {
    if (boundSessionId && boundParticipantId) {
      const room = globalRooms.get(boundSessionId);
      if (room) {
        cleanupParticipant(room, boundParticipantId, isHostRole ? "Host disconnected" : "Disconnected");
      }
    }
  });

  return new Response(null, {
    status: 101,
    webSocket: client,
  });
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
    // Host disconnected — notify guests
    broadcastToRoom(room, {
      type: "server_error",
      sessionId: room.sessionId,
      message: "Host closed the session",
    });
    // Close remaining connections
    for (const [, guest] of room.participants) {
      guest.ws.close(1000, "Host closed session");
    }
    room.participants.clear();
    globalRooms.delete(room.sessionId);
    codeToSession.delete(room.code);
  } else {
    // Guest disconnected — notify others
    broadcastToRoom(room, {
      type: "participant_left",
      sessionId: room.sessionId,
      participantId,
      reason,
    });
  }
}
