import test from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import registry from './registry.json' with {type:'json'};
import {makeParticle} from './simulation.js';

test('dust constructor applies vanilla scale clamp to size and lifetime', () => {
  const original = Math.random;
  Math.random = () => 0.5;
  try {
    const def = registry.find(p => p.id === 'dust');
    const scene = new THREE.Scene();
    const textures = new Map(def.sprites.map(path => [path, new THREE.Texture()]));
    const spawn = {position:[0,0,0],motion:[0.1,0.2,0.3]};
    const create = scale => makeParticle(def,spawn,{scale,color:'#ff0000'},scene,textures);
    const normal=create(1), maximum=create(4), oversized=create(100);
    assert.equal(normal.lifetime,13);
    assert.equal(normal.size,0.11250000000000002);
    assert.equal(maximum.lifetime,normal.lifetime*4);
    assert.equal(maximum.size,normal.size*4);
    assert.equal(oversized.lifetime,maximum.lifetime);
    assert.equal(oversized.size,maximum.size);
    for (const p of [normal,maximum,oversized]) p.dispose();
  } finally {Math.random=original;}
});

test('every declared preview family constructs and advances', () => {
  assert.equal(registry.length,115);
  assert.equal(registry.filter(p=>p.status==='unsupported').length,1);
  const originalDocument=globalThis.document;
  globalThis.document={createElement:()=>({width:64,height:64,getContext:()=>({fillRect(){},strokeRect(){},fillText(){}})})};
  try {
    const cache=new Map();
    for(const def of registry)for(const path of def.sprites)cache.set(path,new THREE.Texture());
    const options={color:'#ffaa33',toColor:'#00ffee',scale:1,power:1,alpha:1,roll:0,delay:0,duration:30,target:[5,2,0]};
    for(const def of registry.filter(p=>p.status!=='unsupported')){
      const scene=new THREE.Scene();
      const particle=makeParticle(def,{position:[0,0,0],motion:[0.1,0.2,0.3]},options,scene,cache);
      assert.ok(particle,`${def.id} did not create a preview`);
      for(let i=0;i<4;i++)particle.tick();
      assert.ok(particle.position.every(Number.isFinite),`${def.id} produced an invalid position`);
      particle.dispose();
    }
  } finally {globalThis.document=originalDocument;}
});
