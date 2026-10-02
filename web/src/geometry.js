export const vec = value => String(value ?? '0;0;0').split(';').map(Number);
export const vecString = values => values.map(n => +n.toFixed(4)).join(';');
export function rotate([x, y, z], degrees) {
  const [a,b,c] = vec(degrees).map(n => n * Math.PI / 180);
  let yy = y * Math.cos(a) - z * Math.sin(a), zz = y * Math.sin(a) + z * Math.cos(a);
  let xx = x * Math.cos(b) + zz * Math.sin(b); zz = -x * Math.sin(b) + zz * Math.cos(b);
  return [xx * Math.cos(c) - yy * Math.sin(c), xx * Math.sin(c) + yy * Math.cos(c), zz];
}
const normal = point => { const len = Math.hypot(...point); return len ? point.map(n => n / len) : [1,0,0]; };
const frac = value => value - Math.floor(value);
const golden = Math.PI * (3 - Math.sqrt(5));
export function samples(type, config) {
  const count = Math.max(1, Math.min(4096, Math.floor(+config.points || 1)));
  const list = [];
  const put = (point, direction) => list.push({point:rotate(point,config.rotation), direction:rotate(direction,config.rotation)});
  if (type === 'base:circle') {
    for (let i=0;i<count;i++) {const a=2*Math.PI*i/count, r=[Math.cos(a),0,Math.sin(a)]; put(r.map(n=>n*(+config.radius||0)),r);}
  } else if (type === 'base:sphere') {
    for (let i=0;i<count;i++) {const y=1-2*(i+0.5)/count, radius=Math.sqrt(1-y*y), a=golden*i, r=[Math.cos(a)*radius,y,Math.sin(a)*radius]; put(r.map(n=>n*(+config.radius||0)),r);}
  } else if (type === 'base:line') {
    for(let i=0;i<count;i++){const x=count===1?0:(+config.length||0)*(i/(count-1)-0.5);put([x,0,0],[x<0?-1:1,0,0]);}
  } else if (type === 'base:rectangle') {
    const w=+config.width||1,h=+config.height||1,p=2*(w+h);
    for(let i=0;i<count;i++){const t=p*i/count;let x,z;if(t<w){x=-w/2+t;z=-h/2;}else if(t<w+h){x=w/2;z=-h/2+t-w;}else if(t<2*w+h){x=w/2-(t-w-h);z=h/2;}else{x=-w/2;z=h/2-(t-2*w-h);}put([x,0,z],normal([x,0,z]));}
  } else if (type === 'base:box_surface') {
    const [sx,sy,sz]=vec(config.size), [hx,hy,hz]=[sx/2,sy/2,sz/2], areas=[sy*sz,sy*sz,sx*sz,sx*sz,sx*sy,sx*sy], total=areas.reduce((a,b)=>a+b,0), counts=[0,0,0,0,0,0];
    let face=0,cumulative=areas[0];for(let i=0;i<count;i++){const pos=total*(i+0.5)/count;while(face<5&&pos>=cumulative)cumulative+=areas[++face];counts[face]++;}
    for(let f=0;f<6;f++)for(let j=0;j<counts[f];j++){const u=2*(j+0.5)/counts[f]-1,v=counts[f]===1?0:2*frac((j+0.5)*(Math.sqrt(5)-1)/2)-1;
      const pairs=[[[hx,u*hy,v*hz],[1,0,0]],[[-hx,u*hy,v*hz],[-1,0,0]],[[u*hx,hy,v*hz],[0,1,0]],[[u*hx,-hy,v*hz],[0,-1,0]],[[u*hx,v*hy,hz],[0,0,1]],[[u*hx,v*hy,-hz],[0,0,-1]]];put(...pairs[f]);}
  }
  return list;
}
