import YAML from 'yaml';
import {emitters} from './emitters.js';
import {parseCondition} from './conditions.js';

const object = value => value && typeof value==='object' && !Array.isArray(value);
export function defaultNodeName(type,config={}){
  return type==='base:particle'?String(config.particle||'particle').replace(/^minecraft:/,''):(emitters.get(type)?.name||type);
}
function webData(value,id){
  if(value==null)return null;
  try{const parsed=typeof value==='string'?JSON.parse(value):value;if(!object(parsed))throw new Error('Expected object');return parsed;}
  catch(error){throw new Error(`Invalid $web JSON at ${id}: ${error.message}`);}
}
export function importGraph(text) {
  const document=YAML.parseDocument(text,{uniqueKeys:true});
  if(document.errors.length)throw document.errors[0];
  const source=document.toJS();
  if(!object(source))throw new Error('Expected a YAML node map');
  const raw=object(source.nodes)&&!source.__root__?source.nodes:source;
  if(!object(raw.__root__)||raw.__root__.type!=='base:root')throw new Error('Missing __root__ emitter');
  const nodes={};
  for(const [id,value] of Object.entries(raw)){
    if(!object(value)||!emitters.has(value.type))throw new Error(`Unknown emitter type at ${id}`);
    if(value.type==='base:root'&&id!=='__root__')throw new Error('Root must be __root__');
    if(value.type==='base:particle'&&value.connections!=null)throw new Error(`Particle ${id} cannot have connections`);
    const {type,connections,$web:webValue,...config}=value,web=webData(webValue,id);
    const edges={};if(connections!=null){if(!object(connections))throw new Error(`Invalid connections at ${id}`);
      for(const [target,condition] of Object.entries(connections)){const expression=condition==null?'':String(condition);parseCondition(expression);edges[target]=expression;}}
    const nameAuto=web?.autoName===true||!web?.name;
    nodes[id]={id,type,config,connections:edges,name:nameAuto?defaultNodeName(type,config):(typeof web.name==='string'&&web.name.trim()?web.name:defaultNodeName(type,config)),nameAuto,x:0,y:0};
  }
  for(const node of Object.values(nodes))for(const target of Object.keys(node.connections))if(!nodes[target])throw new Error(`Unknown connection target: ${target}`);
  const depths=new Map([['__root__',0]]),queue=['__root__'];
  while(queue.length){const id=queue.shift(),depth=depths.get(id);for(const target of Object.keys(nodes[id].connections))if(!depths.has(target)){depths.set(target,depth+1);queue.push(target);}}
  for(const id of Object.keys(nodes))if(!depths.has(id))depths.set(id,Math.max(1,...depths.values())+1);
  const columns=new Map();for(const [id,depth] of depths){const list=columns.get(depth)||[];list.push(id);columns.set(depth,list);}
  let left=60;for(const [,ids] of [...columns].sort((a,b)=>a[0]-b[0])){const width=Math.min(3,ids.length);ids.forEach((id,i)=>{nodes[id].x=left+(i%3)*290;nodes[id].y=ids.length===1?160:60+Math.floor(i/3)*160;});left+=width*290;}
  for(const [id,value] of Object.entries(raw)){
    const position=webData(value.$web,id)?.position;
    if(object(position)&&Number.isFinite(position.x)&&Number.isFinite(position.y)){
      nodes[id].x=position.x;nodes[id].y=position.y;
    }
  }
  return {nodes};
}
export function exportGraph(graph){
  const map={};for(const node of Object.values(graph.nodes)){
    const value={type:node.type,...node.config};
    if(!emitters.get(node.type).terminal)value.connections={...node.connections};
    value.$web=JSON.stringify({name:node.name||defaultNodeName(node.type,node.config),position:{x:node.x,y:node.y},autoName:node.nameAuto!==false});
    map[node.id]=value;
  }
  return YAML.stringify(map,{lineWidth:0});
}
export function newNode(graph,type){
  const def=emitters.get(type);if(!def||type==='base:root')throw new Error('Invalid emitter type');
  const prefix=type.slice(5);let i=1;while(graph.nodes[`${prefix}_${i}`])i++;
  const id=`${prefix}_${i}`,config=structuredClone(def.defaults);graph.nodes[id]={id,type,config,connections:{},name:defaultNodeName(type,config),nameAuto:true,x:380,y:100+Object.keys(graph.nodes).length*34};return id;
}
