import test from 'node:test';
import assert from 'node:assert/strict';
import {packetSpawns, packetSize, varIntBytes} from './packet.js';

test('count zero uses dist times speed as one provider motion', () => {
  const p = {x:1,y:2,z:3,xDist:2,yDist:-3,zDist:4,speed:0.5,count:0};
  assert.deepEqual(packetSpawns(p,()=>{throw Error('no Gaussian for count zero');}),
    [{position:[1,2,3],motion:[1,-1.5,2]}]);
});

test('positive count draws six independent Gaussian samples per particle', () => {
  const p = {x:1,y:2,z:3,xDist:2,yDist:3,zDist:4,speed:0.5,count:2};
  let draw=0;
  const result = packetSpawns(p,()=>++draw);
  assert.equal(draw,12);
  assert.deepEqual(result[0],{position:[3,8,15],motion:[2,2.5,3]});
  assert.deepEqual(result[1],{position:[15,26,39],motion:[5,5.5,6]});
});

test('fixed packet body and particle options are counted', () => {
  assert.deepEqual(packetSize({optionBytes:8},2,1),{payload:55,wire:60});
  assert.equal(varIntBytes(127),1);
  assert.equal(varIntBytes(128),2);
  assert.deepEqual(packetSize({option:'trail',optionBytes:29},2,1,{duration:128}),{payload:77,wire:82});
});
