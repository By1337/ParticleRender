// ClientPacketListener.handleParticleEvent, Minecraft 1.21.11.
export function gaussian() {
  const u = 1 - Math.random();
  const v = Math.random();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

export function packetSpawns(p, randomGaussian = gaussian) {
  const base = [p.x, p.y, p.z];
  const dist = [p.xDist, p.yDist, p.zDist];
  if (p.count === 0) return [{ position: base, motion: dist.map(v => v * p.speed) }];
  const result = [];
  for (let i = 0; i < p.count; i++) {
    result.push({
      position: base.map((v, axis) => v + randomGaussian() * dist[axis]),
      motion: [randomGaussian() * p.speed, randomGaussian() * p.speed, randomGaussian() * p.speed]
    });
  }
  return result;
}

// Packet.write: 2 bool + 3 double + 4 float + int = 46 B.
// Framing and compression marker are deliberately fixed user assumptions.
export function varIntBytes(value) {
  let n = Math.max(0, Math.floor(Number(value) || 0));
  let bytes = 1;
  while (n >= 128 && bytes < 5) {n = Math.floor(n / 128); bytes++;}
  return bytes;
}

export function packetSize(def, packetIdBytes = 2, typeBytes = 1, options = {}) {
  let optionBytes = def.optionBytes;
  if (def.option === 'shriek') optionBytes = varIntBytes(options.delay);
  if (def.option === 'trail') optionBytes = 28 + varIntBytes(options.duration);
  if (def.option === 'vibration') optionBytes = 9 + varIntBytes(options.duration); // block PositionSource type + packed BlockPos + arrival ticks
  const payload = 46 + typeBytes + optionBytes;
  return { payload, wire: payload + 2 + 1 + packetIdBytes };
}
