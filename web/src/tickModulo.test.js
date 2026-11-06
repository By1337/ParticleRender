import test from 'node:test';
import assert from 'node:assert/strict';
import {importGraph,exportGraph,newNode} from './graph.js';
import {evaluateGraph} from './evaluator.js';
import {wrapTick} from './emitters.js';
import {parseCondition,appendConditionClause} from './conditions.js';

test('inclusive modulo bounds, singleton and large ticks',()=>{
  for(const [tick,expected] of [[0,0],[200,200],[201,0],[221,20],[402,0],[2147483647,49]])assert.equal(wrapTick(tick,{max:200}),expected);
  assert.equal(wrapTick(100,{min:20,max:20}),20);
  assert.equal(wrapTick(31,{min:10,max:30}),10);
  assert.equal(wrapTick(2147483647,{max:2147483647}),2147483647);
  for(const config of [{max:-1},{min:2,max:1},{max:2.5},{max:2147483648}])assert.throws(()=>wrapTick(0,config));
});
test('equality conditions and helper',()=>{
  assert.equal(appendConditionClause('', '=', 20),'=20');
  assert.equal(parseCondition('=20')(20),true);
  assert.equal(parseCondition('==20 & %5')(20),true);
  assert.equal(parseCondition('=20')(21),false);
  assert.throws(()=>parseCondition('===20'));
});
test('modulo round trips, propagates local tick through descendants and isolates siblings',()=>{
  const graph=importGraph(`__root__:
  type: base:root
  connections: {loop: '', sibling: '=221'}
loop:
  type: base:tick_modulo
  min: 0
  max: 200
  connections: {line: '=20', nested: ''}
line:
  type: base:line
  points: 1
  length: 0
  connections: {burst: '=20'}
nested:
  type: base:tick_modulo
  max: 9
  connections: {burst: '=0'}
burst:
  type: base:particle
  particle: flame
  count: 1
sibling:
  type: base:particle
  particle: flame
  count: 1
`);
  const restored=importGraph(exportGraph(graph));
  assert.deepEqual(restored.nodes.loop.config,{min:0,max:200});
  assert.equal(restored.nodes.nested.config.min,0);
  const ticks=[];evaluateGraph(restored,221,(_config,ctx)=>ticks.push(ctx.tick));
  assert.deepEqual(ticks,[20,0,221]);
  const id=newNode(graph,'base:tick_modulo');
  assert.deepEqual(graph.nodes[id].config,{min:0,max:200});
});
