import {emitters} from './emitters.js';
import {parseCondition} from './conditions.js';
export function evaluateGraph(graph,tick,onParticle,maxVisits=20000) {
  let visits=0;
  function visit(id,context,depth){
    if(++visits>maxVisits||depth>64)throw new Error('Graph execution limit exceeded (cycle or excessive fan-out)');
    const node=graph.nodes[id],def=emitters.get(node.type);
    for(const next of def.execute(node.config,context)){
      if(def.terminal){onParticle(next.particle,next);continue;}
      for(const [target,condition] of Object.entries(node.connections))if(graph.nodes[target]&&parseCondition(condition)(tick))visit(target,next,depth+1);
    }
  }
  visit('__root__',{x:0,y:0,z:0,xDist:0,yDist:0,zDist:0,tick},0);
  return visits;
}
