import './style.css';
import {definitions,emitters,particleIds,particleRegistry,optionFields} from './emitters.js';
import {importGraph,exportGraph,newNode,defaultNodeName} from './graph.js';
import {parseCondition,appendConditionClause} from './conditions.js';
import {PreviewScene} from './scene.js';
import {vec,vecString} from './geometry.js';

const app=document.getElementById('app');
app.innerHTML=`<header class="topbar"><div class="brand"><span class="brand-mark">✦</span><strong>Particle Graph</strong><small>EDITOR</small></div><div class="toolbar"><button id="open">Import YAML</button><input id="file" type="file" accept=".yml,.yaml,text/yaml" hidden><button id="copy">Copy YAML</button><button id="download" class="primary">Download YAML</button></div></header>
<div class="workspace"><div class="left"><section class="scene-panel"><div class="pane-head"><span>3D PREVIEW</span><div class="scene-controls"><button id="play">▶ Play</button><button id="pause">Ⅱ Pause</button><button id="tick">Tick</button><button id="clear">Clear particles</button><button id="restart">↺ Restart</button><span class="readout">TICK <b id="tick-value">0</b></span><span class="readout">ALIVE <b id="alive-value">0</b></span></div></div><div id="viewport"><div class="scene-metrics"><div><b id="packets-rate">0</b> packets/s</div><div><b id="traffic-rate">0 B/s</b> estimated traffic</div><div><b id="packets-tick">0</b> packets/tick</div><div><b id="traffic-tick">0 B/tick</b> estimated traffic</div></div><div class="scene-legend"><span class="legend-geometry">●</span> geometry samples <span class="legend-packet">●</span> packet origins <span>· particles include packet spread and motion</span></div></div><div class="scene-foot"><span id="camera-hint">Orbit: drag · Pan: right drag · Zoom: wheel</span><div><button id="camera-mode" class="mode">Free camera</button><button id="translate" class="mode active">Move</button><button id="rotate" class="mode">Rotate</button></div></div></section><div id="splitter" title="Drag to resize"></div><section class="graph-panel"><div class="pane-head"><span>EMITTER GRAPH</span><div class="graph-actions"><select id="add-type"><option value="">+ Add node</option>${definitions.filter(d=>d.id!=='base:root').map(d=>`<option value="${d.id}">${d.name}</option>`).join('')}</select><button id="fit">Fit graph</button><span class="readout" id="zoom-value">100%</span></div></div><div id="graph-viewport"><div id="graph-world"><svg id="edges" width="4000" height="3000"></svg><div id="nodes"></div></div><div id="graph-menu" class="graph-menu" hidden>${definitions.filter(d=>d.id!=='base:root').map(d=>`<button data-type="${d.id}">${d.name}</button>`).join('')}</div></div><div class="graph-foot">Drag output to input · Drag an edge grip to reconnect or detach · Right click to add a node</div></section></div><aside class="properties"><div class="property-head"><span>PROPERTIES</span><button id="delete-node" title="Delete selected node">Delete</button></div><div id="property-content"></div><div class="status" id="status">Ready</div></aside></div>`;
const $=id=>document.getElementById(id), esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let graph={nodes:{__root__:{id:'__root__',type:'base:root',config:{},connections:{},name:'Root',nameAuto:true,x:80,y:130}}},selected='__root__',selectedEdge=null,connectionDrag=null,menuPosition=null;
let zoom=1,panX=40,panY=30;
const preview=new PreviewScene($('viewport'),(id,transform)=>{Object.assign(graph.nodes[id].config,transform);renderProperties();},message=>status(message,true));
preview.onTick=(tick,alive)=>{$('tick-value').textContent=tick;$('alive-value').textContent=alive;};
const formatBytes=bytes=>bytes>=1024?`${(bytes/1024).toFixed(1)} KiB`:`${bytes} B`;
preview.onTraffic=(packets,bytes,tickPackets,tickBytes)=>{$('packets-rate').textContent=packets;$('traffic-rate').textContent=`${formatBytes(bytes)}/s`;$('packets-tick').textContent=tickPackets;$('traffic-tick').textContent=`${formatBytes(tickBytes)}/tick`;};
function status(message,error=false){$('status').textContent=message;$('status').classList.toggle('error',error);}
function changed(helper=true){preview.setGraph(graph);if(helper)preview.select(selected);renderGraph();renderProperties();}
function selectNode(id){selected=id;selectedEdge=null;preview.select(id);$('property-content').scrollTop=0;renderGraph();renderProperties();}
function selectEdge(source,target){selectedEdge={source,target};selected=null;preview.select(null);$('property-content').scrollTop=0;renderGraph();renderProperties();}
function worldStyle(){const el=$('graph-world');el.style.transform=`translate(${panX}px,${panY}px) scale(${zoom})`;$('zoom-value').textContent=`${Math.round(zoom*100)}%`;}
function edgePath(a,b){const x1=a.x+208,y1=a.y+52,x2=b.x,y2=b.y+52,dx=Math.max(65,Math.abs(x2-x1)*.48);return `M${x1},${y1} C${x1+dx},${y1} ${x2-dx},${y2} ${x2},${y2}`;}
function edgeGrip(a,b,t){const x1=a.x+208,y1=a.y+52,x2=b.x,y2=b.y+52,dx=Math.max(65,Math.abs(x2-x1)*.48),u=1-t;
  return {x:u*u*u*x1+3*u*u*t*(x1+dx)+3*u*t*t*(x2-dx)+t*t*t*x2,y:u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2};}
function pointInWorld(event){const rect=$('graph-viewport').getBoundingClientRect();return {x:(event.clientX-rect.left-panX)/zoom,y:(event.clientY-rect.top-panY)/zoom};}
function draftPath(from,to){const dx=Math.max(65,Math.abs(to.x-from.x)*.48);return `M${from.x},${from.y} C${from.x+dx},${from.y} ${to.x-dx},${to.y} ${to.x},${to.y}`;}
function updateDraft(event){if(!connectionDrag)return;const point=pointInWorld(event),source=graph.nodes[connectionDrag.source],target=graph.nodes[connectionDrag.target];
  const from=connectionDrag.end==='source'?point:{x:source.x+208,y:source.y+52},to=connectionDrag.end==='source'?{x:target.x,y:target.y+52}:point;
  $('draft-edge')?.setAttribute('d',draftPath(from,to));}
function finishConnectionDrag(event){const drag=connectionDrag;if(!drag)return;connectionDrag=null;window.removeEventListener('pointermove',updateDraft);window.removeEventListener('pointerup',finishConnectionDrag);
  const port=document.elementFromPoint(event.clientX,event.clientY)?.closest('.port');const wanted=drag.end==='source'?'output':'input';const candidate=port?.classList.contains(wanted)?port.closest('.node')?.dataset.id:null;
  const oldTarget=drag.target,oldSource=drag.source,condition=oldTarget?graph.nodes[oldSource].connections[oldTarget]:'';
  if(oldTarget)delete graph.nodes[oldSource].connections[oldTarget];
  if(candidate&&candidate!==(drag.end==='source'?oldTarget:oldSource)){
    const source=drag.end==='source'?candidate:oldSource,target=drag.end==='source'?oldTarget:candidate;
    graph.nodes[source].connections[target]=condition;selected=null;selectedEdge={source,target};preview.select(null);status(`${source} → ${target}`);
  }else if(oldTarget){selectedEdge=null;status(`Disconnected ${oldSource} → ${oldTarget}`);}
  changed();}
function startConnectionDrag(event,source,target=null,end='target'){
  if(event.button!==0)return;event.preventDefault();event.stopPropagation();$('graph-menu').hidden=true;
  connectionDrag={source,target,end};updateDraft(event);window.addEventListener('pointermove',updateDraft);window.addEventListener('pointerup',finishConnectionDrag);}
function renderGraph(){worldStyle();const nodes=Object.values(graph.nodes);
  $('edges').innerHTML=nodes.flatMap(source=>Object.entries(source.connections).map(([target,condition])=>{
    const dest=graph.nodes[target];if(!dest)return '';const path=edgePath(source,dest),middleX=(source.x+208+dest.x)/2,middleY=(source.y+dest.y)/2+52-13,active=selectedEdge?.source===source.id&&selectedEdge.target===target,first=edgeGrip(source,dest,.23),last=edgeGrip(source,dest,.77);
    return `<g class="edge ${active?'selected':''}" data-source="${esc(source.id)}" data-target="${esc(target)}"><path class="edge-hit" d="${path}"/><path class="edge-line" d="${path}"/><circle class="edge-handle source" cx="${first.x}" cy="${first.y}" r="6"/><circle class="edge-handle target" cx="${last.x}" cy="${last.y}" r="6"/><text x="${middleX}" y="${middleY}" text-anchor="middle">${esc(condition||'always')}</text></g>`;}).join('')).join('')+'<path id="draft-edge"/>';
  $('nodes').innerHTML=nodes.map(node=>{const def=emitters.get(node.type),isRoot=node.id==='__root__';return `<div class="node ${selected===node.id?'selected':''}" data-id="${esc(node.id)}" style="left:${node.x}px;top:${node.y}px"><div class="node-title"><span class="node-icon">${isRoot?'◆':def.terminal?'✳':'◯'}</span><span class="node-label" title="${esc(node.name||defaultNodeName(node.type,node.config))}">${esc(node.name||defaultNodeName(node.type,node.config))}</span></div><div class="node-id">${esc(node.id)}</div>${!isRoot?'<button class="port input" title="Drop connection here"></button>':''}${!def.terminal?'<button class="port output" title="Drag connection from here"></button>':''}</div>`;}).join('');
  $('edges').querySelectorAll('.edge').forEach(el=>el.addEventListener('click',e=>{e.stopPropagation();selectEdge(el.dataset.source,el.dataset.target);}));
  $('edges').querySelectorAll('.edge-handle').forEach(el=>el.addEventListener('pointerdown',event=>{const edge=el.closest('.edge');startConnectionDrag(event,edge.dataset.source,edge.dataset.target,el.classList.contains('source')?'source':'target');}));
  $('nodes').querySelectorAll('.node').forEach(el=>{
    const id=el.dataset.id;
    el.querySelector('.output')?.addEventListener('pointerdown',e=>startConnectionDrag(e,id));
    el.querySelector('.input')?.addEventListener('pointerdown',e=>{const incoming=nodes.filter(node=>Object.hasOwn(node.connections,id));if(incoming.length===1)startConnectionDrag(e,incoming[0].id,id);else{e.stopPropagation();selectNode(id);}});
    el.addEventListener('pointerdown',e=>{if(e.target.closest('.port'))return;e.stopPropagation();selectNode(id);const startX=e.clientX,startY=e.clientY,originalX=graph.nodes[id].x,originalY=graph.nodes[id].y;
      const move=event=>{graph.nodes[id].x=Math.round(originalX+(event.clientX-startX)/zoom);graph.nodes[id].y=Math.round(originalY+(event.clientY-startY)/zoom);renderGraph();};
      const up=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);};window.addEventListener('pointermove',move);window.addEventListener('pointerup',up);});
  });
}
function fieldHtml(field,value,prefix='config'){
  const id=`${prefix}.${field.key}`,missing=value==null,kind=field.kind;
  const toggle=field.optional?`<label class="optional"><input type="checkbox" data-optional="${esc(id)}" ${missing?'':'checked'}> enabled</label>`:'';
  let input;
  if(kind==='boolean')input=`<input data-field="${esc(id)}" type="checkbox" ${value?'checked':''}>`;
  else if(kind==='enum')input=`<select data-field="${esc(id)}">${field.values.map(v=>`<option value="${esc(v)}" ${value===v?'selected':''}>${esc(v)}</option>`).join('')}</select>`;
  else if(kind==='particle')input=`<div class="particle-picker"><input class="particle-search" type="text" role="combobox" aria-label="Particle ID" aria-autocomplete="list" aria-expanded="false" autocomplete="off" spellcheck="false" value="${esc(String(value??'flame').replace(/^minecraft:/,''))}"><div class="particle-options" role="listbox" hidden></div></div>`;
  else if(kind==='color'&&String(value||'').length===7)input=`<input data-field="${esc(id)}" type="color" value="${esc(value||'#ffffff')}">`;
  else if(kind==='vec3')input=`<div class="vec3-fields">${vec(value).map((part,index)=>`<label>${'XYZ'[index]}<input data-vec-field="${esc(id)}" data-axis="${index}" type="number" step="any" value="${esc(part)}" ${missing?'disabled':''}></label>`).join('')}</div>`;
  else input=`<input data-field="${esc(id)}" type="${kind==='int'||kind==='double'?'number':'text'}" ${kind==='int'?'step="1"':kind==='double'?'step="any"':''} value="${esc(missing?'':value)}" ${missing?'disabled':''}>`;
  return `<div class="field"><div class="field-label"><span>${esc(field.label)}</span>${toggle}</div>${input}</div>`;
}
function defaultsForOption(option){const defaults={dust:{rgb:'#ffffff',size:1},dust_color_transition:{rgbFrom:'#ffffff',rgbTo:'#ffffff',size:1},block:{block:'stone'},item:{item:'stone'},color:{argb:'#ffffffff'},spell:{argb:'#ffffffff',power:1},power:{power:1},sculk_charge:{roll:0},shriek:{delay:0},trail:{pos:'0;0;0',color:'#ffffff',duration:30},vibration:{origin:'0;0;0',destination:'0;0;0',arrivalInTicks:30},geyser_base:{waterBlocks:1,burstImpulseBase:1},geyser:{waterBlocks:1}};return defaults[option]||null;}
function initParticlePicker(content,node){const picker=content.querySelector('.particle-picker');if(!picker)return;const input=picker.querySelector('input'),list=picker.querySelector('.particle-options');let matches=[],active=0;
  const close=()=>{list.hidden=true;input.setAttribute('aria-expanded','false');};
  const draw=()=>{const query=input.value.trim().toLowerCase().replace(/^minecraft:/,'');matches=particleIds.filter(id=>id.includes(query)).sort((a,b)=>(b.startsWith(query)?1:0)-(a.startsWith(query)?1:0)||a.localeCompare(b));active=0;
    list.innerHTML=matches.length?matches.map((id,index)=>`<button type="button" role="option" data-particle="${esc(id)}" class="${index===active?'active':''}">${esc(id)}</button>`).join(''):'<div class="empty-choice">No matching particle</div>';
    list.hidden=false;input.setAttribute('aria-expanded','true');};
  const commit=id=>{if(!particleRegistry.has(id))return;const old=node.config.particle;node.config.particle=id;
    const option=particleRegistry.get(id)?.option;node.config.data=defaultsForOption(option);if(!node.config.data)delete node.config.data;
    if(node.nameAuto||node.name===defaultNodeName(node.type,{particle:old})){node.name=defaultNodeName(node.type,node.config);node.nameAuto=true;}
    close();changed();status(`Particle set to ${id}`);};
  input.addEventListener('focus',draw);input.addEventListener('input',draw);
  input.addEventListener('keydown',event=>{if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();if(list.hidden)draw();active=(active+(event.key==='ArrowDown'?1:-1)+matches.length)%Math.max(1,matches.length);list.querySelectorAll('button').forEach((button,i)=>button.classList.toggle('active',i===active));list.querySelectorAll('button')[active]?.scrollIntoView({block:'nearest'});}else if(event.key==='Enter'){event.preventDefault();if(matches[active])commit(matches[active]);}else if(event.key==='Escape'){close();input.value=String(node.config.particle).replace(/^minecraft:/,'');input.blur();}});
  list.addEventListener('pointerdown',event=>event.preventDefault());list.addEventListener('click',event=>{const button=event.target.closest('[data-particle]');if(button)commit(button.dataset.particle);});
  input.addEventListener('blur',()=>{if(!list.hidden){const exact=input.value.trim().toLowerCase().replace(/^minecraft:/,'');if(particleRegistry.has(exact)&&exact!==String(node.config.particle).replace(/^minecraft:/,''))commit(exact);else{input.value=String(node.config.particle).replace(/^minecraft:/,'');close();}}});
}
function renderProperties(){const content=$('property-content'),oldScroll=content.scrollTop;$('delete-node').disabled=!selected||selected==='__root__';
  if(selectedEdge){const {source,target}=selectedEdge,value=graph.nodes[source]?.connections[target]??'';content.innerHTML=`<div class="selection-title">Connection</div><p class="edge-summary">${esc(source)} <span>→</span> ${esc(target)}</p><div class="field"><div class="field-label">Condition</div><input id="condition" value="${esc(value)}" placeholder="always"><small>Examples: %3 · &lt;100 &amp; %5 · &gt;=20</small></div><div class="condition-helpers">${[['%','Every N ticks'],['<','Before tick N'],['>','After tick N']].map(([op,label])=>`<label>${label}<span><input type="number" min="${op==='%'?1:0}" step="1" value="${op==='%'?1:0}" data-condition-number="${op}"><button type="button" data-condition-add="${op}">Add</button></span></label>`).join('')}</div><button id="remove-edge" class="danger">Remove connection</button>`;
    const conditionInput=$('condition');const saveCondition=()=>{try{parseCondition(conditionInput.value);graph.nodes[source].connections[target]=conditionInput.value;renderGraph();status('Condition updated');}catch(error){status(error.message,true);conditionInput.value=graph.nodes[source].connections[target]??'';}};
    conditionInput.addEventListener('change',saveCondition);
    content.querySelectorAll('[data-condition-add]').forEach(button=>button.addEventListener('click',()=>{const number=content.querySelector(`[data-condition-number="${button.dataset.conditionAdd}"]`),tick=Number(number.value);try{if(!number.value.trim())throw new Error('Enter a valid tick number');conditionInput.value=appendConditionClause(conditionInput.value,button.dataset.conditionAdd,tick);saveCondition();}catch(error){status(error.message,true);}}));
    $('remove-edge').onclick=()=>{delete graph.nodes[source].connections[target];selectedEdge=null;changed();};return;}
  if(!selected){content.innerHTML='<p class="empty">Select a node or connection.</p>';return;}
  const node=graph.nodes[selected],def=emitters.get(node.type);let html=`<div class="selection-title">${esc(def.name)}</div><div class="field display-name"><div class="field-label">Display name</div><input id="display-name" type="text" value="${esc(node.name||defaultNodeName(node.type,node.config))}" placeholder="${esc(defaultNodeName(node.type,node.config))}"><small>Editor label; connections still use the ID below.</small></div><div class="node-name">ID · ${esc(node.id)}</div><div class="type-id">${esc(node.type)}</div>`;
  if(def.fields.length)html+=`<div class="fields">${def.fields.map(f=>fieldHtml(f,node.config[f.key])).join('')}</div>`;
  if(def.terminal){const particle=particleRegistry.get(String(node.config.particle||'flame').replace(/^minecraft:/,'')),option=particle?.option,fields=optionFields[option]||[];
    html+=`<div class="section-label">PARTICLE OPTIONS <span>${esc(option||'none')}</span></div>`;
    if(fields.length)html+=`<div class="fields">${fields.map(f=>fieldHtml(f,node.config.data?.[f.key],'data')).join('')}</div>`;
    else html+='<p class="empty">This particle has no option data.</p>';
    if(particle)html+=`<p class="hint">${esc(particle.status)} · ${esc(particle.provider)}</p>`;
  }
  if(def.helper)html+='<p class="hint">Use Move or Rotate in the scene. The gizmo writes offsets and rotation here.</p>';
  content.innerHTML=html;
  content.scrollTop=oldScroll;
  $('display-name').addEventListener('input',event=>{const value=event.target.value;node.name=value.trim()?value:defaultNodeName(node.type,node.config);node.nameAuto=!value.trim();const label=$('nodes').querySelectorAll('.node');for(const element of label)if(element.dataset.id===node.id){element.querySelector('.node-label').textContent=node.name;element.querySelector('.node-label').title=node.name;break;}});
  $('display-name').addEventListener('blur',event=>{if(!event.target.value.trim())event.target.value=node.name;});
  initParticlePicker(content,node);
  content.querySelectorAll('[data-optional]').forEach(el=>el.addEventListener('change',()=>{const [section,key]=el.dataset.optional.split('.'),field=(section==='data'?optionFields[particleRegistry.get(String(node.config.particle).replace(/^minecraft:/,''))?.option]:def.fields)?.find(f=>f.key===key),target=section==='data'?(node.config.data??={}):node.config;
    if(el.checked)target[key]=field?.kind==='vec3'?'0;0;0':0;else delete target[key];changed();}));
  content.querySelectorAll('[data-vec-field]').forEach(el=>el.addEventListener('change',()=>{const [section,key]=el.dataset.vecField.split('.'),target=section==='data'?(node.config.data??={}):node.config,value=el.value.trim();if(!value||!Number.isFinite(Number(value))){status(`Invalid ${key} coordinate`,true);renderProperties();return;}
    const numbers=vec(target[key]);numbers[Number(el.dataset.axis)]=Number(value);target[key]=vecString(numbers);changed();status(`${key} updated`);}));
  content.querySelectorAll('[data-field]').forEach(el=>el.addEventListener('change',()=>{const [section,key]=el.dataset.field.split('.');let value=el.type==='checkbox'?el.checked:el.value;
    if(el.type==='number'){value=Number(value);if(!Number.isFinite(value)){status(`Invalid ${key}`,true);return;}if(el.step==='1')value=Math.trunc(value);}
    if(section==='data'){node.config.data??={};node.config.data[key]=value;}else{node.config[key]=value;if(key==='particle'){const option=particleRegistry.get(value)?.option;node.config.data=defaultsForOption(option);if(!node.config.data)delete node.config.data;}}
    changed();status(`${key} updated`);
  }));
}
function fitGraph(){const nodes=Object.values(graph.nodes);if(!nodes.length)return;const minX=Math.min(...nodes.map(n=>n.x)),minY=Math.min(...nodes.map(n=>n.y)),maxX=Math.max(...nodes.map(n=>n.x+208)),maxY=Math.max(...nodes.map(n=>n.y+104));const rect=$('graph-viewport').getBoundingClientRect();zoom=Math.min(1.3,Math.max(.25,Math.min((rect.width-100)/(maxX-minX),(rect.height-80)/(maxY-minY))));panX=(rect.width-(maxX-minX)*zoom)/2-minX*zoom;panY=(rect.height-(maxY-minY)*zoom)/2-minY*zoom;worldStyle();}
function setGraph(next){graph=next;selected='__root__';selectedEdge=null;preview.restart();preview.setGraph(graph);preview.select(selected);preview.onTick(0,0);renderGraph();renderProperties();requestAnimationFrame(fitGraph);}
async function loadText(text){try{setGraph(importGraph(text));status(`${Object.keys(graph.nodes).length} nodes imported`);}catch(error){status(error.message,true);}}
$('open').onclick=()=>$('file').click();$('file').onchange=async event=>{const file=event.target.files[0];if(file)await loadText(await file.text());event.target.value='';};
$('copy').onclick=async()=>{try{await navigator.clipboard.writeText(exportGraph(graph));status('YAML copied');}catch(error){status(`Clipboard unavailable: ${error.message}`,true);}};
$('download').onclick=()=>{const blob=new Blob([exportGraph(graph)],{type:'text/yaml'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='particle-graph.yml';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);status('YAML downloaded');};
$('add-type').onchange=e=>{if(!e.target.value)return;const id=newNode(graph,e.target.value);e.target.value='';changed();selectNode(id);};
$('delete-node').onclick=()=>{if(!selected||selected==='__root__')return;for(const node of Object.values(graph.nodes))delete node.connections[selected];delete graph.nodes[selected];selectNode('__root__');changed();};
$('play').onclick=()=>{preview.running=true;status('Playing');};$('pause').onclick=()=>{preview.running=false;status('Paused');};$('tick').onclick=()=>{preview.running=false;preview.step();};$('clear').onclick=()=>{preview.clearParticles();status('Particles cleared');};$('restart').onclick=()=>{preview.restart();preview.onTick(0,0);status('Preview restarted');};
$('camera-mode').onclick=()=>{preview.setFreeCamera(!preview.freeCamera);$('camera-mode').classList.toggle('active',preview.freeCamera);$('camera-mode').textContent=preview.freeCamera?'Orbit camera':'Free camera';$('camera-hint').textContent=preview.freeCamera?'Free: hold RMB · WASD/ЦФЫВ · Space/E up · Q/Shift down · Wheel zoom/speed':'Orbit: drag · Pan: right drag · Zoom: wheel';};
$('translate').onclick=()=>{preview.setMode('translate');$('translate').classList.add('active');$('rotate').classList.remove('active');};$('rotate').onclick=()=>{preview.setMode('rotate');$('rotate').classList.add('active');$('translate').classList.remove('active');};
$('fit').onclick=fitGraph;
const viewport=$('graph-viewport');viewport.addEventListener('wheel',e=>{e.preventDefault();const old=zoom;zoom=Math.max(.25,Math.min(2.5,zoom*(e.deltaY<0?1.1:.9)));const rect=viewport.getBoundingClientRect(),mx=e.clientX-rect.left,my=e.clientY-rect.top;panX=mx-(mx-panX)*zoom/old;panY=my-(my-panY)*zoom/old;worldStyle();},{passive:false});
viewport.addEventListener('contextmenu',e=>{e.preventDefault();const rect=viewport.getBoundingClientRect(),menu=$('graph-menu');menuPosition=pointInWorld(e);menu.hidden=false;menu.style.left=`${Math.max(0,Math.min(e.clientX-rect.left,rect.width-150))}px`;menu.style.top=`${Math.max(0,Math.min(e.clientY-rect.top,rect.height-235))}px`;});
$('graph-menu').addEventListener('pointerdown',e=>e.stopPropagation());
$('graph-menu').querySelectorAll('[data-type]').forEach(button=>button.onclick=()=>{const id=newNode(graph,button.dataset.type);graph.nodes[id].x=Math.round(menuPosition.x);graph.nodes[id].y=Math.round(menuPosition.y);$('graph-menu').hidden=true;changed();selectNode(id);status(`${id} created`);});
viewport.addEventListener('pointerdown',e=>{if(e.button!==0)return;if(!e.target.closest('#graph-menu'))$('graph-menu').hidden=true;if(e.target.closest('.node,.edge,#graph-menu'))return;selected=null;selectedEdge=null;preview.select(null);renderGraph();renderProperties();const sx=e.clientX,sy=e.clientY,px=panX,py=panY;const move=event=>{panX=px+event.clientX-sx;panY=py+event.clientY-sy;worldStyle();};const up=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);};window.addEventListener('pointermove',move);window.addEventListener('pointerup',up);});
window.addEventListener('keydown',e=>{if(e.key==='Escape'){$('graph-menu').hidden=true;if(connectionDrag){connectionDrag=null;window.removeEventListener('pointermove',updateDraft);window.removeEventListener('pointerup',finishConnectionDrag);$('draft-edge')?.setAttribute('d','');}}});
const splitter=$('splitter');splitter.addEventListener('pointerdown',e=>{e.preventDefault();const left=document.querySelector('.left'),box=left.getBoundingClientRect();const move=event=>{const pct=Math.max(20,Math.min(80,(event.clientY-box.top)/box.height*100));left.style.setProperty('--scene-height',`${pct}%`);preview.resize();};const up=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);};window.addEventListener('pointermove',move);window.addEventListener('pointerup',up);});
window.addEventListener('dragover',e=>e.preventDefault());window.addEventListener('drop',async e=>{e.preventDefault();const file=e.dataTransfer.files[0];if(file)await loadText(await file.text());});
fetch(`${import.meta.env.BASE_URL}ref.yml`).then(r=>r.text()).then(loadText).catch(()=>setGraph(graph));
