export const CAMERA_MOVE_SPEED = 6;
export const CAMERA_LOOK_SENSITIVITY = 0.0025;
export const CAMERA_MIN_SPEED = 1;
export const CAMERA_MAX_SPEED = 40;
const PITCH_LIMIT = Math.PI / 2 - 0.02;

export const clampPitch = pitch => Math.max(-PITCH_LIMIT, Math.min(PITCH_LIMIT, pitch));
export const cameraKey = code => ({KeyW:'w',KeyA:'a',KeyS:'s',KeyD:'d',KeyQ:'q',KeyE:'e',Space:'space',ShiftLeft:'shift',ShiftRight:'shift'}[code]);
export const wheelCameraSpeed = (speed,deltaY) => Math.max(CAMERA_MIN_SPEED,Math.min(CAMERA_MAX_SPEED,speed*Math.exp(-deltaY*.001)));

// Horizontal WASD movement follows camera yaw; Space/E and Q/Shift remain world vertical.
export function movementVector(yaw, keys) {
  const forward = (keys.has('w') ? 1 : 0) - (keys.has('s') ? 1 : 0);
  const strafe = (keys.has('d') ? 1 : 0) - (keys.has('a') ? 1 : 0);
  const vertical = (keys.has('e') || keys.has('space') ? 1 : 0) - (keys.has('q') || keys.has('shift') ? 1 : 0);
  const x = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
  const z = -Math.cos(yaw) * forward - Math.sin(yaw) * strafe;
  const length = Math.hypot(x, vertical, z);
  return length ? [x / length, vertical / length, z / length] : [0, 0, 0];
}
