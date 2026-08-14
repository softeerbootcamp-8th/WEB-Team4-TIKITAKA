export function estimateServerOffsetMs(
  serverTime: number,
  clientRequestedAt: number,
  roundTripTimeMs: number,
): number {
  const safeRoundTripTimeMs = Math.max(0, roundTripTimeMs)
  return serverTime - clientRequestedAt - safeRoundTripTimeMs / 2
}
