import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {importGraph,exportGraph,newNode} from './graph.js';
import {evaluateGraph} from './evaluator.js';
import {parseCondition,appendConditionClause} from './conditions.js';
import {samples} from './geometry.js';
import * as THREE from 'three';

const fixture=readFileSync(new URL('../public/ref.yml',import.meta.url),'utf8');
test('ref.yml imports all registered emitters and round trips semantically',()=>{
  const graph=importGraph(fixture);
  assert.equal(Object.keys(graph.nodes).length,7);
  assert.deepEqual(importGraph(exportGraph(graph)).nodes.circle.config,graph.nodes.circle.config);
  assert.equal(graph.nodes.__root__.connections.circle,'%3');
  assert.equal(graph.nodes.a1.config.data.rgb,'#ffffff');
  assert.equal(graph.nodes.a1.config.maxSpeed,0.1);
  assert.equal(graph.nodes.a1.name,'dust');
  assert.deepEqual([graph.nodes.a1.x,graph.nodes.a1.y],[importGraph(exportGraph(graph)).nodes.a1.x,importGraph(exportGraph(graph)).nodes.a1.y]);
});
test('condition parser matches Java comparison and period conjunction',()=>{
  const condition=parseCondition('<100 & %5');
  assert.equal(condition(0),true);assert.equal(condition(5),true);assert.equal(condition(6),false);assert.equal(condition(100),false);
  assert.throws(()=>parseCondition('%0'));
});
test('condition helpers append Java clauses to manual input',()=>{
  const condition=appendConditionClause(appendConditionClause(appendConditionClause('', '%', 5), '<', 100), '>', 2);
  assert.equal(condition,'%5 & <100 & >2');
  assert.equal(parseCondition(condition)(5),true);
  assert.equal(parseCondition(condition)(2),false);
  assert.throws(()=>appendConditionClause('', '%', 0));
});
test('root, circle, particle execution passes positions and directions',()=>{
  const graph=importGraph(`__root__:\n  type: base:root\n  connections: {circle: '%2'}\ncircle:\n  type: base:circle\n  points: 4\n  radius: 2\n  offsets: '1;2;3'\n  dist: outward\n  connections: {particle: ''}\nparticle:\n  type: base:particle\n  particle: flame\n  count: 0\n`);
  const output=[];evaluateGraph(graph,0,(_config,context)=>output.push(context));
  assert.equal(output.length,4);assert.deepEqual([output[0].x,output[0].y,output[0].z],[3,2,3]);assert.equal(output[0].xDist,1);
  output.length=0;evaluateGraph(graph,1,(_config,context)=>output.push(context));assert.equal(output.length,0);
  assert.equal(samples('base:circle',{points:4,radius:2,rotation:'0;0;0'}).length,4);
});
test('new particle is terminal and serializes without connections',()=>{
  const graph=importGraph(fixture),id=newNode(graph,'base:particle');
  assert.equal(graph.nodes[id].type,'base:particle');
  assert.equal(graph.nodes[id].name,'flame');
  assert.ok(!Object.hasOwn(importGraph(exportGraph(graph)).nodes[id].config,'connections'));
});
test('$web name and position are metadata, not emitter config',()=>{
  const graph=importGraph(fixture);
  graph.nodes.a1.name='My dust cloud';graph.nodes.a1.nameAuto=false;graph.nodes.a1.x=731;graph.nodes.a1.y=143;
  const yaml=exportGraph(graph),restored=importGraph(yaml);
  assert.match(yaml,/\$web:/);
  assert.equal(restored.nodes.a1.name,'My dust cloud');
  assert.equal(restored.nodes.a1.x,731);
  assert.equal(restored.nodes.a1.y,143);
  assert.ok(!Object.hasOwn(restored.nodes.a1.config,'$web'));
  assert.equal(restored.nodes.a1.connections.particle,undefined);
});
test('automatic particle label follows the particle type after import',()=>{
  const graph=importGraph(fixture),yaml=exportGraph(graph).replace('particle: dust','particle: flame');
  assert.equal(importGraph(yaml).nodes.a1.name,'flame');
});
test('geometry helper rotation matches evaluator samples for circle and box',()=>{
  for(const [type,config] of [
    ['base:circle',{points:16,radius:3,rotation:'90;20;35',offsets:'1;2;3'}],
    ['base:box_surface',{points:18,size:'4;3;2',rotation:'20;30;10',offsets:'1;2;3'}]
  ]){
    const raw=samples(type,{...config,rotation:'0;0;0'}),rotated=samples(type,config);
    const group=new THREE.Group();const [rx,ry,rz]=config.rotation.split(';').map(Number);group.rotation.set(...[rx,ry,rz].map(THREE.MathUtils.degToRad),'ZYX');
    for(let i=0;i<raw.length;i++){
      const transformed=new THREE.Vector3(...raw[i].point).applyEuler(group.rotation);
      for(let axis=0;axis<3;axis++)assert.ok(Math.abs(transformed.getComponent(axis)-rotated[i].point[axis])<1e-9,`${type} point ${i} axis ${axis}`);
    }
  }
});
