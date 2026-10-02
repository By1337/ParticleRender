import test from 'node:test';
import assert from 'node:assert/strict';
import {clampPitch,movementVector,cameraKey,wheelCameraSpeed,CAMERA_MOVE_SPEED} from './cameraNavigation.js';
import {PreviewScene} from './scene.js';
import * as THREE from 'three';

test('free camera movement follows yaw and keeps Q/E vertical',()=>{
  const north=movementVector(0,new Set(['w']));
  assert.ok(Math.abs(north[0])<1e-12);
  assert.equal(north[2],-1);
  const west=movementVector(Math.PI/2,new Set(['w']));
  assert.ok(Math.abs(west[0]+1)<1e-12);
  assert.ok(Math.abs(west[2])<1e-12);
  assert.equal(movementVector(0,new Set(['e']))[1],1);
  assert.equal(movementVector(0,new Set(['space']))[1],1);
  assert.equal(movementVector(0,new Set(['q']))[1],-1);
  assert.equal(movementVector(0,new Set(['shift']))[1],-1);
  assert.ok(CAMERA_MOVE_SPEED>0);
});

test('physical camera keys also work with Russian layout and wheel changes speed',()=>{
  assert.equal(cameraKey('KeyW'),'w');
  assert.equal(cameraKey('KeyA'),'a');
  assert.equal(cameraKey('Space'),'space');
  assert.equal(cameraKey('ShiftLeft'),'shift');
  assert.equal(cameraKey('ShiftRight'),'shift');
  assert.equal(wheelCameraSpeed(6,-120)>6,true);
  assert.equal(wheelCameraSpeed(6,120)<6,true);
});

test('free camera pitch cannot flip over',()=>{
  assert.ok(clampPitch(100)<Math.PI/2);
  assert.ok(clampPitch(-100)>-Math.PI/2);
});

test('free camera moves only during right-button look mode',()=>{
  const preview=Object.create(PreviewScene.prototype);
  preview.freeLook=false;preview.freeKeys=new Set();preview.freeYaw=0;preview.cameraMoveSpeed=CAMERA_MOVE_SPEED;
  preview.camera=new THREE.PerspectiveCamera();preview.renderer={domElement:{style:{}}};
  preview.beginFreeLook({clientX:20,clientY:30});
  preview.freeKeys.add('w');preview.updateFreeCamera(1);
  assert.equal(preview.camera.position.z,-CAMERA_MOVE_SPEED);
  preview.endFreeLook();preview.updateFreeCamera(1);
  assert.equal(preview.camera.position.z,-CAMERA_MOVE_SPEED);
  assert.equal(preview.freeKeys.size,0);
});
