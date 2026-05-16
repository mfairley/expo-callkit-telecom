import { randomUUID } from "node:crypto";

export interface IncomingCallEvent {
  eventId: string;
  serverCallId: string;
  hasVideo: boolean;
  startedAt: string;
  caller: {
    id: string;
    displayName?: string;
    avatarUrl?: string;
    phoneNumber?: string;
    email?: string;
  };
  metadata?: Record<string, unknown>;
}

export interface BuildEventArgs {
  eventId?: string;
  serverCallId?: string;
  hasVideo?: boolean;
  callerId?: string;
  displayName?: string;
  phoneNumber?: string;
}

export function buildEvent(args: BuildEventArgs = {}): IncomingCallEvent {
  if (args.phoneNumber && !/^\+[1-9]\d{1,14}$/.test(args.phoneNumber)) {
    throw new Error(
      `phoneNumber must be E.164 (e.g. +14155551234), got: ${args.phoneNumber}`,
    );
  }
  return {
    eventId: args.eventId ?? randomUUID(),
    serverCallId: args.serverCallId ?? `test-${Date.now()}`,
    hasVideo: args.hasVideo ?? false,
    startedAt: new Date().toISOString(),
    caller: {
      id: args.callerId ?? "test-caller",
      displayName: args.displayName ?? "Test Caller",
      ...(args.phoneNumber ? { phoneNumber: args.phoneNumber } : {}),
    },
  };
}
