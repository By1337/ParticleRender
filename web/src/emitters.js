import {samples, vec} from './geometry.js';
import registry from './vanilla/registry.json' with {type:'json'};

const field = (key, kind, label=key, extra={}) => ({key,kind,label,...extra});
const geometryFields = [field('points','int'),field('offsets','vec3','Offsets',{optional:true}),field('rotation','vec3','Rotation'),field('dist','enum','Direction',{values:['none','outward','inward']})];
const geometry = (id,name,defaults,dimensionFields) => ({id,name,defaults:{...defaults,points:64,rotation:'0;0;0',dist:'none'},fields:[...geometryFields,...dimensionFields],
  execute(config,context){const [ox,oy,oz]=vec(config.offsets);return samples(id,config).map(({point,direction})=>({
    ...context,x:context.x+ox+point[0],y:context.y+oy+point[1],z:context.z+oz+point[2],
    ...((config.dist??'none')==='none'?{}:{xDist:direction[0]*(config.dist==='inward'?-1:1),yDist:direction[1]*(config.dist==='inward'?-1:1),zDist:direction[2]*(config.dist==='inward'?-1:1)})
  }));}, helper:true});

export const particleRegistry = new Map(registry.map(item=>[item.id,item]));
export const particleIds = registry.map(item=>item.id).sort();
export const optionFields = {
  dust:[field('rgb','color','RGB'),field('size','double','Size')],
  dust_color_transition:[field('rgbFrom','color','From'),field('rgbTo','color','To'),field('size','double','Size')],
  block:[field('block','string','Block ID')], item:[field('item','string','Item ID')],
  color:[field('argb','color','ARGB')], spell:[field('argb','color','ARGB'),field('power','double','Power')],
  power:[field('power','double','Power')],sculk_charge:[field('roll','double','Roll')],shriek:[field('delay','int','Delay')],
  trail:[field('pos','vec3','Target'),field('color','color','Color'),field('duration','int','Duration')],
  vibration:[field('origin','vec3','Origin'),field('destination','vec3','Destination'),field('arrivalInTicks','int','Arrival ticks')],
  geyser_base:[field('waterBlocks','int'),field('burstImpulseBase','double')],geyser:[field('waterBlocks','int')]
};
export const definitions = [
  {id:'base:root',name:'Root',defaults:{},fields:[],execute:(_c,ctx)=>[ctx]},
  geometry('base:circle','Circle',{radius:2},[field('radius','double')]),
  geometry('base:sphere','Sphere',{radius:2},[field('radius','double')]),
  geometry('base:line','Line',{length:4},[field('length','double')]),
  geometry('base:rectangle','Rectangle',{width:4,height:3},[field('width','double'),field('height','double')]),
  geometry('base:box_surface','Box surface',{size:'4;3;2'},[field('size','vec3')]),
  {id:'base:particle',name:'Particle',terminal:true,defaults:{particle:'flame',count:0,maxSpeed:0,overrideLimiter:false,alwaysShow:false},
    fields:[field('particle','particle'),field('count','int'),field('maxSpeed','double','Max speed'),field('offsets','vec3','Offsets',{optional:true}),field('dist','vec3','Dist override',{optional:true}),field('overrideLimiter','boolean'),field('alwaysShow','boolean')],
    execute(config,context){const [ox,oy,oz]=vec(config.offsets), dist=config.dist==null?[context.xDist,context.yDist,context.zDist]:vec(config.dist);return [{...context,x:context.x+ox,y:context.y+oy,z:context.z+oz,xDist:dist[0],yDist:dist[1],zDist:dist[2],particle:config}];}}
];
export const emitters = new Map(definitions.map(def=>[def.id,def]));
export function previewOptions(config) {
  const data=config.data||{}, type=particleRegistry.get(String(config.particle).replace(/^minecraft:/,''))?.option;
  const argb=String(data.argb||'#ffffffff');
  return {color:data.rgb||data.rgbFrom||data.color||(argb.length===9?'#'+argb.slice(3):argb.length===7?argb:'#ffffff'),
    toColor:data.rgbTo||'#ffffff',scale:+data.size||1,alpha:argb.length===9?parseInt(argb.slice(1,3),16)/255:1,
    power:+data.power||1,roll:+data.roll||0,delay:+data.delay||0,duration:+(data.duration??data.arrivalInTicks)||30,
    target:vec(data.pos??data.destination),block:data.block||'stone',item:data.item||'stone',optionType:type};
}
