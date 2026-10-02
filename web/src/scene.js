import * as THREE from 'three';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {TransformControls} from 'three/addons/controls/TransformControls.js';
import registry from './vanilla/registry.json' with {type:'json'};
import {packetSpawns,packetSize} from './vanilla/packet.js';
import {makeParticle,setPreviewRandom} from './vanilla/simulation.js';
import {particleRegistry,previewOptions,emitters} from './emitters.js';
import {samples,vec,vecString} from './geometry.js';
import {evaluateGraph} from './evaluator.js';
import {CAMERA_MOVE_SPEED,CAMERA_LOOK_SENSITIVITY,clampPitch,cameraKey,wheelCameraSpeed,movementVector} from './cameraNavigation.js';

export class PreviewScene {
  constructor(host,onTransform,onError){
    this.host=host;this.onTransform=onTransform;this.onError=onError;this.tick=0;this.particles=[];this.seed=0x13579bdf;this.selected=null;this.packetEvents=[];
    this.scene=new THREE.Scene();this.scene.background=new THREE.Color(0x101821);
    this.camera=new THREE.PerspectiveCamera(55,1,.01,1000);this.camera.position.set(10,7,11);
    this.renderer=new THREE.WebGLRenderer({antialias:true});this.renderer.setPixelRatio(Math.min(devicePixelRatio,2));host.append(this.renderer.domElement);
    this.orbit=new OrbitControls(this.camera,this.renderer.domElement);this.orbit.enableDamping=true;this.orbit.target.set(0,1,0);this.orbit.update();
    this.freeCamera=false;this.freeLook=false;this.freeKeys=new Set();this.freeYaw=0;this.freePitch=0;this.freePointerX=0;this.freePointerY=0;this.cameraMoveSpeed=CAMERA_MOVE_SPEED;
    host.addEventListener('contextmenu',event=>event.preventDefault());
    host.addEventListener('pointerdown',event=>{if(event.button!==2||!this.freeCamera||this.gizmo.dragging)return;
      event.preventDefault();this.beginFreeLook(event);});
    window.addEventListener('pointermove',event=>{if(!this.freeLook)return;
      if(!(event.buttons&2)){this.endFreeLook();return;}
      const dx=event.clientX-this.freePointerX,dy=event.clientY-this.freePointerY;
      this.freePointerX=event.clientX;this.freePointerY=event.clientY;
      this.freeYaw-=dx*CAMERA_LOOK_SENSITIVITY;this.freePitch=clampPitch(this.freePitch-dy*CAMERA_LOOK_SENSITIVITY);
      this.camera.rotation.set(this.freePitch,this.freeYaw,0,'YXZ');});
    window.addEventListener('pointerup',event=>{if(event.button===2||!(event.buttons&2))this.endFreeLook();});
    window.addEventListener('pointercancel',()=>this.endFreeLook());
    document.addEventListener('keydown',event=>{if(!this.freeLook)return;
      if(event.target instanceof Element&&event.target.closest('input,textarea,select,[contenteditable]')){this.freeKeys.clear();return;}
      const key=cameraKey(event.code);if(key){this.freeKeys.add(key);event.preventDefault();}});
    document.addEventListener('keyup',event=>{const key=cameraKey(event.code);if(key)this.freeKeys.delete(key);});
    document.addEventListener('focusin',event=>{if(event.target instanceof Element&&event.target.closest('input,textarea,select,[contenteditable]'))this.freeKeys.clear();});
    window.addEventListener('blur',()=>this.endFreeLook());
    host.addEventListener('wheel',event=>{if(!this.freeCamera)return;event.preventDefault();
      const direction=new THREE.Vector3();this.camera.getWorldDirection(direction);
      const distance=Math.max(.1,Math.min(4,Math.abs(event.deltaY)*.01))*this.cameraMoveSpeed/CAMERA_MOVE_SPEED;
      this.camera.position.addScaledVector(direction,event.deltaY<0?distance:-distance);
      if(this.freeLook)this.cameraMoveSpeed=wheelCameraSpeed(this.cameraMoveSpeed,event.deltaY);
    },{passive:false});
    this.scene.add(new THREE.GridHelper(60,60,0x63849a,0x354b5b));this.scene.add(new THREE.AxesHelper(1.5));
    this.gizmo=new TransformControls(this.camera,this.renderer.domElement);this.scene.add(this.gizmo.getHelper());
    this.gizmo.addEventListener('dragging-changed',event=>{this.orbit.enabled=!this.freeCamera&&!event.value;});
    this.gizmo.addEventListener('objectChange',()=>{if(!this.selected||!this.helper)return;const p=this.helper.position,r=this.helper.rotation;
      this.onTransform(this.selected,{offsets:vecString([p.x,p.y,p.z]),rotation:vecString([r.x,r.y,r.z].map(n=>THREE.MathUtils.radToDeg(n)))});});
    this.helper=null;this.helpers=new Map();this.textureCache=new Map();const loader=new THREE.TextureLoader();
    for(const entry of registry)for(const path of entry.sprites)if(!this.textureCache.has(path)){
      const texture=loader.load(`${import.meta.env.BASE_URL}${path}`);texture.magFilter=THREE.NearestFilter;texture.minFilter=THREE.NearestFilter;texture.colorSpace=THREE.SRGBColorSpace;this.textureCache.set(path,texture);
    }
    this.resizeObserver=new ResizeObserver(()=>this.resize());this.resizeObserver.observe(host);this.resize();
    this.running=false;this.last=performance.now();this.accum=0;
    const animate=now=>{requestAnimationFrame(animate);const delta=Math.min(.1,(now-this.last)/1000);this.last=now;
      if(this.running){this.accum+=delta*20;while(this.accum>=1){this.accum--;this.step();}}
      this.reportTraffic(now);if(this.freeCamera)this.updateFreeCamera(delta);else this.orbit.update();this.renderer.render(this.scene,this.camera);};requestAnimationFrame(animate);
    setPreviewRandom(()=>this.random());
  }
  random(){this.seed=(Math.imul(this.seed,1664525)+1013904223)>>>0;return this.seed/4294967296;}
  resize(){const w=Math.max(1,this.host.clientWidth),h=Math.max(1,this.host.clientHeight);this.camera.aspect=w/h;this.camera.updateProjectionMatrix();this.renderer.setSize(w,h);}
  setFreeCamera(enabled){
    if(this.freeCamera===enabled)return;this.endFreeLook();this.freeCamera=enabled;this.orbit.enabled=!enabled;
    if(enabled){const rotation=new THREE.Euler().setFromQuaternion(this.camera.quaternion,'YXZ');this.freeYaw=rotation.y;this.freePitch=rotation.x;this.camera.rotation.set(this.freePitch,this.freeYaw,0,'YXZ');}
    else {const forward=new THREE.Vector3();this.camera.getWorldDirection(forward);this.orbit.target.copy(this.camera.position).addScaledVector(forward,5);this.orbit.update();}
  }
  beginFreeLook(event){this.freeLook=true;this.freeKeys.clear();if(event.shiftKey)this.freeKeys.add('shift');this.freePointerX=event.clientX;this.freePointerY=event.clientY;this.renderer.domElement.style.cursor='grabbing';}
  endFreeLook(){this.freeLook=false;this.freeKeys.clear();if(this.renderer)this.renderer.domElement.style.cursor='';}
  updateFreeCamera(delta){
    if(!this.freeLook||!this.freeKeys.size)return;
    const [x,y,z]=movementVector(this.freeYaw,this.freeKeys);
    this.camera.position.addScaledVector(new THREE.Vector3(x,y,z),delta*this.cameraMoveSpeed);
  }
  setGraph(graph){this.graph=graph;this.updateHelper();this.updatePacketOrigins();}
  select(id){this.selected=id;this.gizmo.detach();this.helper=this.helpers.get(id)||null;if(this.helper)this.gizmo.attach(this.helper);for(const [key,group] of this.helpers)group.traverse(obj=>{if(obj.isPoints)obj.material.color.setHex(key===id?0x78d8ff:0x467c96);});}
  setMode(mode){this.gizmo.setMode(mode);}
  updateHelper(){
    this.gizmo.detach();for(const group of this.helpers.values()){this.scene.remove(group);group.traverse(obj=>{obj.geometry?.dispose();obj.material?.dispose();});}this.helpers.clear();this.helper=null;
    for(const node of Object.values(this.graph?.nodes||{})){const def=emitters.get(node.type);if(!def?.helper)continue;
    const group=new THREE.Group();group.name=node.id;
    const local=samples(node.type,{...node.config,rotation:'0;0;0'});const points=local.map(s=>new THREE.Vector3(...s.point));
    if(points.length){const positions=new Float32Array(points.flatMap(p=>p.toArray()));const geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.BufferAttribute(positions,3));
      const dots=new THREE.Points(geo,new THREE.PointsMaterial({color:node.id===this.selected?0x78d8ff:0x467c96,size:.075,sizeAttenuation:true}));group.add(dots);
      if(node.type==='base:circle'||node.type==='base:rectangle'||node.type==='base:line'){
        const linePoints=node.type==='base:line'?points:[...points,points[0]];
        group.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(linePoints),new THREE.LineBasicMaterial({color:node.id===this.selected?0x39bdf2:0x346b83,transparent:true,opacity:.8})));
      }
    }
    group.add(new THREE.Mesh(new THREE.SphereGeometry(.09,12,8),new THREE.MeshBasicMaterial({color:0xffc36b})));
    const [x,y,z]=vec(node.config.offsets),[rx,ry,rz]=vec(node.config.rotation);group.position.set(x,y,z);group.rotation.set(rx*Math.PI/180,ry*Math.PI/180,rz*Math.PI/180,'ZYX');
    this.scene.add(group);this.helpers.set(node.id,group);}
    this.helper=this.helpers.get(this.selected)||null;if(this.helper)this.gizmo.attach(this.helper);
  }
  updatePacketOrigins(){
    if(this.packetMarkers){this.scene.remove(this.packetMarkers);this.packetMarkers.geometry.dispose();this.packetMarkers.material.dispose();this.packetMarkers=null;}
    if(!this.graph)return;
    const origins=[];
    try{evaluateGraph(this.graph,this.tick,(_config,context)=>{if(origins.length<5000)origins.push(context.x,context.y,context.z);});}
    catch(error){this.onError(error.message);return;}
    if(!origins.length)return;
    const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(origins,3));
    this.packetMarkers=new THREE.Points(geometry,new THREE.PointsMaterial({color:0xffba68,size:0.11,sizeAttenuation:true,depthTest:false}));
    this.packetMarkers.renderOrder=2;this.scene.add(this.packetMarkers);
  }
  clearParticles(){this.particles.forEach(p=>p.dispose());this.particles=[];this.onTick?.(this.tick,0);}
  restart(){this.clearParticles();this.tick=0;this.seed=0x13579bdf;this.accum=0;this.packetEvents=[];this.lastPacketTick={count:0,bytes:0};this.updatePacketOrigins();this.reportTraffic(performance.now());}
  reportTraffic(now){this.packetEvents=this.packetEvents.filter(event=>now-event.at<1000);const packets=this.packetEvents.reduce((sum,event)=>sum+event.count,0),bytes=this.packetEvents.reduce((sum,event)=>sum+event.bytes,0);this.onTraffic?.(packets,bytes,this.lastPacketTick?.count??0,this.lastPacketTick?.bytes??0);}
  step(){if(!this.graph)return;
    try{
      let packetCount=0,packetBytes=0;
      const children=[];this.particles=this.particles.filter(p=>{const alive=p.tick();if(p.pending?.length)children.push(...p.pending.splice(0));if(!alive)p.dispose();return alive;});
      for(const child of children)this.spawn(child.id,child.spawn,{});
      evaluateGraph(this.graph,this.tick,(config,context)=>{
        const id=String(config.particle).replace(/^minecraft:/,'');const def=particleRegistry.get(id);if(!def)return;
        const count=Math.max(0,Math.min(5000,Math.floor(+config.count||0)));
        const packet={x:context.x,y:context.y,z:context.z,xDist:context.xDist,yDist:context.yDist,zDist:context.zDist,speed:+config.maxSpeed||0,count};
        packetCount++;packetBytes+=packetSize(def,2,1,previewOptions(config)).wire;
        const gaussian=()=>Math.sqrt(-2*Math.log(1-this.random()))*Math.cos(2*Math.PI*this.random());
        for(const spawn of packetSpawns(packet,gaussian))this.spawn(id,spawn,previewOptions(config));
      });this.packetEvents.push({at:performance.now(),count:packetCount,bytes:packetBytes});this.lastPacketTick={count:packetCount,bytes:packetBytes};this.tick++;this.updatePacketOrigins();this.onTick?.(this.tick,this.particles.length);this.reportTraffic(performance.now());
    }catch(error){this.running=false;this.onError(error.message);}
  }
  spawn(id,spawn,options){if(this.particles.length>=10000)return;const def=particleRegistry.get(id);if(!def)return;
    const particle=makeParticle(def,spawn,{color:'#ffffff',toColor:'#ffffff',scale:1,alpha:1,power:1,roll:0,delay:0,duration:30,target:[0,0,0],...options},this.scene,this.textureCache);
    if(particle)this.particles.push(particle);
  }
}
