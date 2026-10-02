import * as THREE from 'three';

let randomSource = () => Math.random();
export const setPreviewRandom = source => { randomSource = source; };
const rand = () => randomSource();
const between = (n) => (rand() * 2 - 1) * n;
const baseVelocity = (motion) => {
  // Particle(double xSpeed, ...) adds local jitter, normalizes, then lifts Y.
  const v = motion.map(x => x + between(0.4));
  const length = Math.hypot(...v) || 1;
  const magnitude = (rand() + rand() + 1) * 0.15 * 0.4;
  return v.map((x, i) => x / length * magnitude + (i === 1 ? 0.1 : 0));
};
const hexColor = (value) => new THREE.Color(value);

function placeholderTexture(label, choice, cache) {
  const key = `placeholder:${label}:${choice}`;
  if (!cache.has(key)) {
    const canvas = document.createElement('canvas'); canvas.width=64; canvas.height=64;
    const ctx=canvas.getContext('2d'); ctx.fillStyle=label==='item'?'#d5b86a':'#8c9c91';ctx.fillRect(7,7,50,50);
    ctx.strokeStyle='#e8f4ee';ctx.lineWidth=3;ctx.strokeRect(7,7,50,50);
    ctx.fillStyle='#1a2730';ctx.font='bold 22px sans-serif';ctx.textAlign='center';ctx.fillText(choice.slice(0,2).toUpperCase(),32,41);
    const texture=new THREE.CanvasTexture(canvas);texture.magFilter=THREE.NearestFilter;cache.set(key,texture);
  }
  return cache.get(key);
}
const randomLife = (numerator, offset=0.2) => Math.floor(numerator/(rand()*0.8+offset));
const clamp = (v,min,max) => Math.max(min,Math.min(max,v));
const color = (r,g,b) => new THREE.Color(r,g,b);

export function makeParticle(def, spawn, options, scene, textureCache) {
  if(['HugeExplosionSeedParticle','GustSeedParticle'].includes(def.model)){
    const large=def.model==='HugeExplosionSeedParticle';
    const lifetime=large?8:def.id==='gust_emitter_large'?7:3;
    const spread=large?4:def.id==='gust_emitter_large'?3:1;
    const delay=large?0:def.id==='gust_emitter_large'?0:2;
    return {def,position:spawn.position.slice(),velocity:[0,0,0],providerMotion:spawn.motion.slice(),age:0,lifetime,
      renderedSize:0,material:null,frame:0,pending:[],
      tick(){
        if(this.age%(delay+1)===0)for(let i=0;i<(large?6:3);i++){
          this.pending.push({id:large?'explosion':'gust',spawn:{
            position:this.position.map(v=>v+(rand()-rand())*spread),
            motion:[this.age/this.lifetime,0,0]}});
        }
        if(large)return ++this.age<this.lifetime;
        return this.age++<this.lifetime;
      },dispose(){}};
  }
  if (!def.model || (!def.sprites.length && def.status !== 'placeholder')) return null;
  const model = def.model;
  const position = spawn.position.slice();
  let velocity = baseVelocity(spawn.motion);
  let lifetime = Math.floor(4 / (rand() * 0.9 + 0.1));
  let size = 0.1 * (rand() * 0.5 + 0.5) * 2;
  let friction = 0.98, gravity = 0, alpha = 1;
  let tint = new THREE.Color(0xffffff);
  let animate = false, randomSprite = false, mode='base', grow=false, roll=0, spin=0, target=options.target, toTint=null;
  if (model === 'FlameParticle' || model === 'SoulParticle') {
    velocity = velocity.map((v, i) => v * 0.01 + spawn.motion[i]);
    for (let i = 0; i < 3; i++) position[i] += (rand() - rand()) * 0.05;
    friction = 0.96;
    lifetime = Math.floor(8 / (rand() * 0.8 + 0.2)) + 4;
    if (def.id === 'small_flame') size *= 0.5;
    if (model === 'SoulParticle') {size*=1.5;animate=true;}
    else randomSprite = true;
  } else if (['SmokeParticle','LargeSmokeParticle','WhiteSmokeParticle','AshParticle','WhiteAshParticle','DustPlumeParticle'].includes(model)) {
    velocity = baseVelocity([0, 0, 0]).map((v, i) => v * 0.1 + spawn.motion[i]);
    friction = 0.96; gravity = ['AshParticle','DustPlumeParticle'].includes(model) ? (model==='AshParticle'?0.1:0.5) : model==='WhiteAshParticle'?0.0125:-0.1;
    const scale = def.id === 'large_smoke' ? 2.5 : 1;
    size *= 0.75 * scale;
    lifetime = Math.max(1, Math.floor((['AshParticle','WhiteAshParticle'].includes(model)?20:model==='DustPlumeParticle'?7:8) / (rand() * 0.8 + 0.2) * scale));
    const c = rand() * (model==='AshParticle'?0.5:0.3);
    tint = ['WhiteSmokeParticle','WhiteAshParticle'].includes(model) ? new THREE.Color(0xbab1c2) : model==='DustPlumeParticle'?new THREE.Color(0xbab1c2).addScalar(-rand()*0.2):new THREE.Color(c, c, c);
    if (model==='AshParticle'||model==='WhiteAshParticle') {
      const input=model==='WhiteAshParticle'?[rand()*-1.9*rand()*0.1,rand()*-0.5*rand()*0.5,rand()*-1.9*rand()*0.1]:[0,0,0];
      velocity=baseVelocity([0,0,0]).map((v,i)=>v*[0.1,-0.1,0.1][i]+input[i]);
    }
    if (model==='DustPlumeParticle') velocity=baseVelocity([0,0,0]).map((v,i)=>v*[0.7,0.6,0.7][i]+spawn.motion[i]+(i===1?0.15:0));
    animate = true;
  } else if (['DustParticle','DustColorTransitionParticle'].includes(model)) {
    velocity = baseVelocity(spawn.motion).map(v => v * 0.1);
    friction = 0.96;
    const effectiveScale = clamp(options.scale,0.01,4);
    size *= 0.75 * effectiveScale;
    lifetime = Math.max(1, Math.floor(randomLife(8) * effectiveScale));
    tint = hexColor(options.color);
    const shade = rand() * 0.4 + 0.6;
    tint.multiplyScalar(shade * (rand() * 0.2 + 0.8));
    if(model==='DustColorTransitionParticle')toTint=hexColor(options.toColor).multiplyScalar(shade*(rand()*0.2+0.8));
    animate = true;
  } else if (model === 'CritParticle') {
    velocity = baseVelocity([0, 0, 0]).map((v, i) => v * 0.1 + spawn.motion[i] * 0.4);
    friction = 0.7; gravity = 0.5;
    size *= 0.75;
    lifetime = Math.max(1, Math.floor(6 / (rand() * 0.8 + 0.6)));
    const c = rand() * 0.3 + 0.6;
    tint = new THREE.Color(c, c, c);
    if (def.id==='damage_indicator') {velocity[1]+=0.4;lifetime=20;}
    if (def.id==='enchanted_hit') tint.multiply(new THREE.Color(0.3,0.8,1));
    randomSprite = true;
  } else if (model === 'NoteParticle') {
    velocity = baseVelocity([0, 0, 0]).map(v => v * 0.01);
    velocity[1] += 0.2;
    friction = 0.66; lifetime = 6; size *= 1.5;
    const phase = spawn.motion[0];
    tint = new THREE.Color(...[0, 1 / 3, 2 / 3].map(o => Math.max(0, Math.sin((phase + o) * Math.PI * 2) * 0.65 + 0.35)));
    randomSprite = true;
  } else if (['PortalParticle','ReversePortalParticle'].includes(model)) {
    velocity = spawn.motion.slice();
    size = 0.1 * (rand() * 0.2 + 0.5) * (def.id === 'reverse_portal' ? 1.5 : 1);
    lifetime = def.id === 'reverse_portal' ? Math.floor(rand() * 2) + 60 : Math.floor(rand() * 10) + 40;
    const c = rand() * 0.6 + 0.4;
    tint = new THREE.Color(c * 0.9, c * 0.3, c);
    randomSprite = true; mode=model==='ReversePortalParticle'?'reversePortal':'portal';
  } else if (['ExplodeParticle','SpitParticle'].includes(model)) {
    velocity = spawn.motion.map(v => v + between(0.05));
    friction = 0.9; gravity = model==='SpitParticle'?0.5:-0.1;
    size = 0.1 * (rand() * rand() * 6 + 1);
    lifetime = Math.floor(16 / (rand() * 0.8 + 0.2)) + 2;
    const c = rand() * 0.3 + 0.7; tint = new THREE.Color(c, c, c);
    animate = true;
  } else if (['EndRodParticle','TotemParticle'].includes(model)) {
    velocity = spawn.motion.slice();
    friction = model==='TotemParticle'?0.6:0.91; gravity = model==='TotemParticle'?1.25:0.0125;
    size *= 0.75; lifetime = 60 + Math.floor(rand() * 12);
    if(model==='TotemParticle') tint=rand()<0.25?color(0.6+rand()*0.2,0.6+rand()*0.3,rand()*0.2):color(0.1+rand()*0.2,0.4+rand()*0.3,rand()*0.2);
    animate = true;
  } else if (model === 'FlyTowardsPositionParticle' || model === 'FlyStraightTowardsParticle') {
    velocity=spawn.motion.slice(); mode=model==='FlyTowardsPositionParticle'?'flyTowards':'flyStraight';
    lifetime=model==='FlyStraightTowardsParticle'?25+Math.floor(rand()*5):30+Math.floor(rand()*10);
    size=0.1*(rand()*0.5+0.2)*(def.id==='vault_connection'?1.5:def.id==='ominous_spawning'?3+rand()*2:1);
    if(mode==='flyTowards') {
      const c=rand()*0.6+0.4;tint=color(c*0.9,c*0.9,c);
      if(def.id==='vault_connection')alpha=0;
    } else {tint=new THREE.Color(0x45aefe);alpha=1;}
    for(let i=0;i<3;i++) position[i]+=velocity[i];
    randomSprite=true;
  } else if (model === 'SpellParticle') {
    // SpellParticle ignores incoming X/Z and samples fresh horizontal motion.
    velocity=baseVelocity([0.5-rand(),spawn.motion[1],0.5-rand()]);
    velocity[1]*=0.2;
    if(spawn.motion[0]===0&&spawn.motion[2]===0){velocity[0]*=0.1;velocity[2]*=0.1;}
    friction=0.96;gravity=-0.1;size*=0.75;lifetime=randomLife(8);
    if(['effect','instant_effect','entity_effect'].includes(def.id)) tint=hexColor(options.color);
    if(def.id==='entity_effect')alpha=options.alpha;
    if(def.id==='instant_effect'||def.id==='effect') velocity=velocity.map((v,i)=>i===1?(v-0.1)*options.power+0.1:v*options.power);
    if(def.id==='witch'){const c=rand()*0.5+0.35;tint=color(c,0,c);}
    animate=true;grow=true;
  } else if (['TerrainParticle','BlockMarker','FallingDustParticle','BreakingItemParticle'].includes(model)) {
    if(model==='BlockMarker'){velocity=[0,0,0];lifetime=80;size=0.5;mode='static';}
    else if(model==='FallingDustParticle'){
      velocity=[0,0,0];size*=0.675;lifetime=Math.max(1,Math.floor(randomLife(32)*0.9));
      tint=hexColor('#bdb8aa');mode='fallingDust';animate=true;
      roll=rand()*Math.PI*2;spin=(rand()-0.5)*0.1*Math.PI*2;
    } else if(model==='TerrainParticle'){
      size*=0.5;gravity=1;tint=color(0.6,0.6,0.6);
      if(def.id==='dust_pillar'){velocity=[between(0.033),spawn.motion[1]+between(0.5),between(0.033)];lifetime=20+Math.floor(rand()*20);}
      if(def.id==='block_crumble'){velocity=[0,0,0];lifetime=1+Math.floor(rand()*10);}
    } else {
      velocity=baseVelocity([0,0,0]).map((v,i)=>v*0.1+(def.id==='item'?spawn.motion[i]:0));
      gravity=1;size*=0.5;
    }
    randomSprite=false;
  } else if (model==='SuspendedParticle') {
    position[1]-=0.125;velocity=[0,0,0];friction=1;size*=rand()*0.6+(def.id==='underwater'?0.2:0.6);lifetime=randomLife(16);
    if(def.id==='underwater')tint=color(0.4,0.4,0.7);
    if(def.id==='spore_blossom_air'){velocity=baseVelocity([0,-0.8,0]);lifetime=500+Math.floor(rand()*501);gravity=0.01;tint=color(0.32,0.5,0.22);}
    if(def.id==='crimson_spore'){velocity=baseVelocity([between(1e-6),between(1e-4),between(1e-6)]);tint=color(0.9,0.4,0.5);}
    if(def.id==='warped_spore'){velocity=baseVelocity([0,-rand()*1.9*rand()*0.1,0]);tint=color(0.1,0.1,0.3);}
    randomSprite=true;
  } else if (model==='SuspendedTownParticle') {
    velocity=baseVelocity(spawn.motion).map(v=>v*0.02);size*=rand()*0.6+0.5;lifetime=randomLife(20);
    const c=rand()*0.1+0.2;tint=color(c,c,c);
    if(['happy_villager','composter','egg_crack'].includes(def.id))tint=color(1,1,1);
    if(def.id==='composter')lifetime=3+Math.floor(rand()*5);
    if(def.id==='dolphin'){tint=color(0.3,0.5,1);alpha=1-rand()*0.7;lifetime=Math.floor(lifetime/2);}
    mode='town';randomSprite=true;
  } else if(model==='HeartParticle'){
    if(def.id==='angry_villager')position[1]+=0.5;
    velocity=baseVelocity([0,0,0]).map(v=>v*0.01);velocity[1]+=0.1;
    friction=0.86;size*=1.5;lifetime=16;randomSprite=true;
  } else if(model==='PlayerCloudParticle'){
    velocity=baseVelocity([0,0,0]).map((v,i)=>v*0.1+spawn.motion[i]);
    friction=0.96;size*=1.875;lifetime=Math.max(1,Math.floor(randomLife(8,0.3)*2.5));
    const c=1-rand()*0.3;tint=color(c,c,c);
    if(def.id==='sneeze'){tint=color(0.22,1,0.53);alpha=0.4;}
    animate=true;grow=true;
  } else if(model==='GlowParticle'){
    velocity=baseVelocity(spawn.motion);friction=0.96;size*=0.75;animate=true;
    if(def.id==='glow'){
      velocity=baseVelocity([0.5-rand(),spawn.motion[1],0.5-rand()]);velocity[1]*=0.2;
      if(spawn.motion[0]===0&&spawn.motion[2]===0){velocity[0]*=0.1;velocity[2]*=0.1;}
      tint=rand()<0.5?color(0.6,1,0.8):color(0.08,0.4,0.4);lifetime=randomLife(8);
    }else{
      const factor=def.id==='electric_spark'?0.25:0.01;
      velocity=spawn.motion.map((v,i)=>v*factor*(i!==1&&def.id.startsWith('wax_')?0.5:1));
      lifetime=def.id==='electric_spark'?2+Math.floor(rand()*2):10+Math.floor(rand()*30);
      tint=def.id==='wax_on'?color(0.91,0.55,0.08):def.id==='scrape'?rand()<0.5?color(0.29,0.58,0.51):color(0.43,0.77,0.62):color(1,0.9,1);
    }
  } else if(['SculkChargeParticle','SculkChargePopParticle'].includes(model)){
    velocity=spawn.motion.slice();friction=0.96;size*=model==='SculkChargeParticle'?1.5:1;
    lifetime=model==='SculkChargeParticle'?8+Math.floor(rand()*12):6+Math.floor(rand()*4);
    roll=options.roll||0;animate=true;
  } else if(model==='DragonBreathParticle'){
    velocity=spawn.motion.map((v,i)=>i===1?(v-0.1)*options.power+0.1:v*options.power);
    friction=0.96;size*=0.75;lifetime=randomLife(20);tint=color(0.717+rand()*0.157,0,0.823+rand()*0.153);
    animate=true;grow=true;mode='dragon';
  } else if(['BubbleParticle','BubbleColumnUpParticle'].includes(model)){
    velocity=spawn.motion.map(v=>v*0.2+between(0.02));
    size*=rand()*0.6+0.2;lifetime=randomLife(model==='BubbleParticle'?8:40);
    friction=0.85;gravity=model==='BubbleParticle'?-0.05:-0.125;
    mode='waterBubble';randomSprite=true;
  } else if(model==='BubblePopParticle'){
    velocity=spawn.motion.slice();lifetime=4;gravity=0.008;animate=true;mode='bubblePop';
  } else if(['WaterDropParticle','SplashParticle','WakeParticle'].includes(model)){
    velocity=baseVelocity([0,0,0]).map(v=>v*0.3);velocity[1]=rand()*0.2+0.1;
    gravity=model==='SplashParticle'?0.04:model==='WakeParticle'?0:0.06;lifetime=randomLife(8);
    if(model==='SplashParticle'&&spawn.motion[1]===0){velocity[0]=spawn.motion[0];velocity[1]=0.1;velocity[2]=spawn.motion[2];}
    if(model==='WakeParticle'){velocity=spawn.motion.slice();mode='wake';animate=true;}
    else mode='waterDrop';
    randomSprite=!animate;
  } else if(model==='WaterCurrentDownParticle'){
    velocity=[0,-0.05,0];lifetime=30+Math.floor(rand()*60);size*=rand()*0.6+0.2;mode='currentDown';
    randomSprite=true;
  } else if(model==='DripParticle'){
    velocity=[0,0,0];gravity=0.06;mode=def.id.startsWith('dripping_')?'dripHang':'dripFall';
    lifetime=mode==='dripHang'?40:randomLife(64);
    if(def.id.startsWith('landing_')){mode='dripLand';lifetime=randomLife(16);}
    if(def.id.includes('honey')){gravity*=0.01;if(mode==='dripHang')lifetime=100;if(mode==='dripLand')lifetime=randomLife(128);}
    if(def.id.includes('obsidian_tear')){gravity=mode==='dripHang'?0.06*0.02*0.01:0.01;if(mode==='dripHang')lifetime=100;if(mode==='dripLand')lifetime=randomLife(28);}
    if(def.id==='falling_nectar'){gravity=0.007;lifetime=randomLife(16);}
    if(def.id==='falling_spore_blossom'){gravity=0.005;lifetime=Math.floor(64/(rand()*0.8+0.1));}
    if(mode==='dripHang'&&!def.id.includes('honey')&&!def.id.includes('obsidian_tear'))gravity*=0.02;
    if(def.id.includes('water'))tint=color(0.2,0.3,1);
    else if(def.id.includes('lava'))tint=color(1,0.286,0.083);
    else if(def.id.includes('honey'))tint=color(0.62,0.48,0.08);
    else if(def.id.includes('obsidian_tear'))tint=color(0.512,0.031,0.891);
    else if(def.id.includes('nectar'))tint=color(0.92,0.782,0.72);
    else if(def.id.includes('spore'))tint=color(0.32,0.5,0.22);
    randomSprite=true;
  } else if(model==='CampfireSmokeParticle'){
    velocity=[spawn.motion[0],spawn.motion[1]+rand()/500,spawn.motion[2]];
    size*=3;lifetime=(def.id.includes('signal')?280:80)+Math.floor(rand()*50);
    gravity=3e-6;alpha=def.id.includes('signal')?0.95:0.9;mode='campfire';randomSprite=true;
  } else if(['GustParticle','AttackSweepParticle','HugeExplosionParticle','SonicBoomParticle'].includes(model)){
    velocity=[0,0,0];mode='static';animate=true;
    if(model==='GustParticle'){lifetime=12+Math.floor(rand()*4);size=def.id==='small_gust'?0.15:1;}
    if(model==='AttackSweepParticle'){lifetime=4;size=1-spawn.motion[0]*0.5;}
    if(model==='HugeExplosionParticle'){lifetime=6+Math.floor(rand()*4);size=2*(1-spawn.motion[0]*0.5);}
    if(model==='SonicBoomParticle'){lifetime=16;size=1.5;}
    const c=rand()*0.6+0.4;tint=color(c,c,c);
  } else if(model==='TrialSpawnerDetectionParticle'){
    velocity=baseVelocity([0,0,0]).map((v,i)=>v*(i===1?0.9:0)+spawn.motion[i]);
    friction=0.96;gravity=-0.1;size*=1.125;lifetime=Math.floor(12/(rand()*0.5+0.5));animate=true;
  } else if(model==='SquidInkParticle'){
    velocity=spawn.motion.slice();friction=0.92;size=0.5;lifetime=Math.floor(6/(rand()*0.8+0.2));
    tint=def.id==='glow_squid_ink'?color(0.2,0.8,0.6):color(0,0,0);animate=true;mode='squid';
  } else if(model==='SnowflakeParticle'){
    velocity=spawn.motion.map(v=>v+between(0.05));gravity=0.225;friction=1;
    size=0.1*(rand()*rand()+1);lifetime=randomLife(16)+2;tint=color(0.923,0.964,0.999);animate=true;mode='snowflake';
  } else if(model==='FireflyParticle'){
    velocity=baseVelocity([0.5-rand(),rand()<0.5?spawn.motion[1]:-spawn.motion[1],0.5-rand()]).map(v=>v*0.8);
    friction=0.96;size*=1.125;lifetime=200+Math.floor(rand()*101);alpha=0;mode='firefly';randomSprite=true;
  } else if(model==='TrailParticle'){
    velocity=baseVelocity(spawn.motion);size=0.26;lifetime=Math.max(1,options.duration);
    tint=hexColor(options.color);tint.multiply(color(0.875+rand()*0.25,0.875+rand()*0.25,0.875+rand()*0.25));mode='target';randomSprite=true;
  } else if(model==='VibrationSignalParticle'){
    velocity=[0,0,0];size=0.3;lifetime=Math.max(1,options.duration);mode='target';randomSprite=true;
  } else if(model==='ShriekParticle'){
    velocity=[0,0.1,0];size=0.85;lifetime=30;gravity=0;mode='shriek';randomSprite=true;
  } else if(model==='LavaParticle'){
    velocity=baseVelocity([0,0,0]).map(v=>v*0.8);velocity[1]=rand()*0.4+0.05;
    gravity=0.75;friction=0.999;size*=rand()*2+0.2;lifetime=randomLife(16);mode='lava';randomSprite=true;
  } else if(model==='FallingLeavesParticle'){
    velocity=[0,def.id==='cherry_leaves'?0:-0.021,0];friction=1;
    gravity=(def.id==='cherry_leaves'?0.25:0.07)*1.2*0.0025;
    lifetime=300;size=(def.id==='cherry_leaves'?1:2)*(rand()<0.5?0.05:0.075);
    tint=def.id==='tinted_leaves'?hexColor(options.color):color(1,1,1);mode='leaves';randomSprite=true;
  } else if(model==='FireworkParticles'){
    velocity=spawn.motion.slice();friction=0.91;gravity=0.1;size*=0.75;
    lifetime=def.id==='flash'?4:48+Math.floor(rand()*12);
    if(def.id==='flash'){velocity=[0,0,0];alpha=options.alpha;tint=hexColor(options.color);size=1;mode='flash';}
    else {alpha=0.99;animate=true;mode='simpleAnimated';}
  } else {
    return null;
  }
  const spriteIndex = randomSprite ? Math.floor(rand() * def.sprites.length) : 0;
  const kind=model==='BreakingItemParticle'?'item':'block';
  const map = def.status==='placeholder'?placeholderTexture(kind,kind==='item'?(options.item||def.id):(options.block||'stone'),textureCache):textureCache.get(def.sprites[spriteIndex]);
  const material = new THREE.SpriteMaterial({map, color: tint, transparent: true, depthWrite: false, opacity: alpha});
  const sprite = new THREE.Sprite(material);
  sprite.position.set(...position);
  sprite.scale.setScalar(size * 2);
  sprite.material.rotation=roll;
  scene.add(sprite);
  const particle = {sprite, material, position, start: position.slice(), origin:spawn.position.slice(), providerMotion:spawn.motion.slice(),pending:[],
    velocity, age: 0, lifetime, size, renderedSize:size, frame:spriteIndex, delay:options.delay||0,
    friction, gravity, alpha, tint, toTint, animate, randomSprite, def, options, mode, grow, target, spin,
    tick() {
      if (this.mode==='shriek'&&this.delay>0){this.delay--;return true;}
      if (this.age++ >= this.lifetime) {
        if(this.mode==='dripHang'){
          const childId=def.id.replace(/^dripping_/, 'falling_');
          this.pending.push({id:childId,spawn:{position:this.position.slice(),motion:this.velocity.slice()}});
        }
        return false;
      }
      const t = this.age / this.lifetime;
      if(this.mode==='portal'){
        const f=1+t-2*t*t;
        this.position[0]=this.start[0]+this.velocity[0]*f;
        this.position[1]=this.start[1]+this.velocity[1]*f+1-t;
        this.position[2]=this.start[2]+this.velocity[2]*f;
      }else if(this.mode==='reversePortal'){
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i]*t;
      }else if(this.mode==='flyTowards'){
        const f=1-t;
        this.position[0]=this.origin[0]+this.velocity[0]*f;
        this.position[1]=this.origin[1]+this.velocity[1]*f-1.2*t*t*t*t;
        this.position[2]=this.origin[2]+this.velocity[2]*f;
      }else if(this.mode==='flyStraight'){
        for(let i=0;i<3;i++)this.position[i]=this.origin[i]+this.velocity[i]*(1-t);
        this.material.color.copy(new THREE.Color(0x45aefe)).lerp(new THREE.Color(0xffffff),t);
        this.material.opacity=1;
      }else if(this.mode==='target'){
        const f=1/Math.max(1,this.lifetime-this.age);
        for(let i=0;i<3;i++)this.position[i]+=(this.target[i]-this.position[i])*f;
      }else if(this.mode==='static'||this.mode==='flash'){
        // Age and frame advance; location does not.
      }else if(this.mode==='currentDown'){
        const angle=this.age*0.08;
        this.velocity[0]=(this.velocity[0]+0.6*Math.cos(angle))*0.07;
        this.velocity[2]=(this.velocity[2]+0.6*Math.sin(angle))*0.07;
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
      }else if(this.mode==='fallingDust'){
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
        this.velocity[1]=Math.max(this.velocity[1]-0.003,-0.14);
        this.material.rotation+=this.spin;
      }else if(this.mode==='campfire'){
        this.velocity[0]+=between(0.0002);this.velocity[2]+=between(0.0002);
        this.velocity[1]-=this.gravity;
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
        if(this.age>=this.lifetime-60)this.material.opacity=Math.max(0,this.material.opacity-0.015);
      }else if(this.mode==='dripHang'||this.mode==='dripFall'||this.mode==='dripLand'){
        this.velocity[1]-=this.gravity;
        for(let i=0;i<3;i++){this.position[i]+=this.velocity[i];this.velocity[i]*=0.98;}
        if(this.mode==='dripHang')for(let i=0;i<3;i++)this.velocity[i]*=0.02;
      }else if(this.mode==='dragon'){
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
        this.velocity[0]*=this.friction;this.velocity[2]*=this.friction;
      }else if(this.mode==='waterBubble'){
        this.velocity[1]+=model==='BubbleParticle'?0.002:0.04*this.gravity*-1;
        for(let i=0;i<3;i++){this.position[i]+=this.velocity[i];this.velocity[i]*=0.85;}
      }else if(this.mode==='bubblePop'){
        this.velocity[1]-=this.gravity;
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
      }else if(this.mode==='waterDrop'||this.mode==='wake'){
        this.velocity[1]-=this.gravity;
        for(let i=0;i<3;i++){this.position[i]+=this.velocity[i];this.velocity[i]*=0.98;}
      }else if(this.mode==='town'){
        for(let i=0;i<3;i++){this.position[i]+=this.velocity[i];this.velocity[i]*=0.99;}
      }else if(this.mode==='leaves'){
        const wind=def.id==='cherry_leaves'?2:10;
        this.velocity[0]+=Math.cos(t*15)*wind*t*0.0025;
        this.velocity[2]+=Math.sin(t*15)*wind*t*0.0025;
        this.velocity[1]-=this.gravity;
        for(let i=0;i<3;i++)this.position[i]+=this.velocity[i];
        this.material.rotation+=0.03;
      }else{
        if(model==='DustPlumeParticle'){this.gravity*=0.88;this.friction*=0.92;}
        this.velocity[1]-=0.04*this.gravity;
        for(let i=0;i<3;i++){this.position[i]+=this.velocity[i];this.velocity[i]*=this.friction;}
        if(this.mode==='snowflake'){this.velocity[0]*=0.95;this.velocity[1]*=0.9;this.velocity[2]*=0.95;}
        if(this.mode==='firefly'&&(this.age===1||rand()>0.95))this.velocity=[between(0.05),between(0.05),between(0.05)];
        if(this.mode==='squid')this.velocity[1]-=0.0074;
      }
      this.sprite.position.set(...this.position);
      let scale = this.size;
      if (model === 'FlameParticle') scale *= 1 - t * t * 0.5;
      if (model === 'LavaParticle') scale *= 1 - t*t;
      if (this.mode === 'portal') scale *= 1 - (1 - t) ** 2;
      if (this.mode === 'reversePortal') scale *= 1-t/1.5;
      if (this.mode === 'shriek') {scale*=Math.min(t*0.75,1);this.material.opacity=1-t;}
      if (this.grow) scale *= Math.min(t * 32, 1);
      if(['SmokeParticle','LargeSmokeParticle','WhiteSmokeParticle','AshParticle','WhiteAshParticle','DustPlumeParticle','DustParticle','DustColorTransitionParticle','CritParticle','NoteParticle','HeartParticle','TrialSpawnerDetectionParticle','FallingDustParticle'].includes(model))scale*=Math.min(t*32,1);
      if(this.mode==='flyTowards'&&def.id==='vault_connection')this.material.opacity=clamp((t-0.25)/(1-0.25),0,1)*0.6;
      if(this.mode==='firefly')this.material.opacity=t>=0.7?(1-t)/0.3:t<=0.5?t/0.5:1;
      if(this.mode==='flash'){
        scale=7.1*Math.sin((this.age-1)*0.25*Math.PI);
        this.material.opacity=0.6-(this.age-1)*0.125;
      }
      if(this.mode==='squid'&&this.age>this.lifetime/2)this.material.opacity=1-(this.age-this.lifetime/2)/this.lifetime;
      if(this.mode==='simpleAnimated'&&this.age>this.lifetime/2)this.material.opacity=1-(this.age-this.lifetime/2)/this.lifetime;
      this.sprite.scale.setScalar(Math.max(0.001, scale * 2));
      this.renderedSize=scale;
      if (['EndRodParticle','TotemParticle'].includes(model) && this.age > this.lifetime / 2) {
        this.material.opacity = 1 - (this.age - this.lifetime / 2) / this.lifetime;
        if(model==='EndRodParticle')this.material.color.lerp(new THREE.Color(0xf2d9c9), 0.2);
      }
      if (model === 'DustColorTransitionParticle') {
        this.material.color.copy(this.tint).lerp(this.toTint, this.age/(this.lifetime+1));
      }
      if (model === 'CritParticle') {
        this.material.color.g *= 0.96; this.material.color.b *= 0.9;
      }
      if(model==='LavaParticle'&&rand()>t)this.pending.push({id:'smoke',spawn:{position:this.position.slice(),motion:this.velocity.slice()}});
      if (this.animate) {
        this.frame = Math.min(def.sprites.length - 1, Math.floor(this.age * (def.sprites.length - 1) / this.lifetime));
        this.material.map = textureCache.get(def.sprites[this.frame]);
      }
      return true;
    },
    dispose() { scene.remove(sprite); material.dispose(); }
  };
  if(model==='CritParticle')particle.tick(); // CritParticle constructor calls tick once.
  return particle;
}
