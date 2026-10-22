const { chromium } = require('playwright');
const R=process.env.R||"/tmp/r2";const fs=require('fs');const have=f=>{try{return fs.statSync(f).size>1500}catch(e){return false}};const T=require('./timeline.json');
(async()=>{
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});
 const mode=process.argv[2];
 if(mode==='screens'){            // the TV and the phone, 12 a second, the whole film
  const p=await b.newPage({viewport:{width:1920,height:1300}});await p.goto('file://'+__dirname+'/screens.html');
  const tv=await p.$('#tv'),ps=await p.$('#ps');const N=Math.round(T.EV.total*12);
  for(let i=0;i<N;i++){const t=i/12,n=String(i+1).padStart(4,'0');if(have(`${R}/phs/${n}.png`)&&have(`${R}/tvs/${n}.png`))continue;await p.evaluate(t=>render(t),t);
   await p.evaluate(()=>{document.getElementById('phone').style.visibility='hidden';document.getElementById('tv').style.visibility='visible'});await tv.screenshot({path:`${R}/tvs/${n}.png`});
   await p.evaluate(()=>{document.getElementById('phone').style.visibility='visible';document.getElementById('tv').style.visibility='hidden'});await ps.screenshot({path:`${R}/phs/${n}.png`});}
 } else if(mode==='tv24'){        // the TV alone, 24 a second, where it is laid over a plate (RANGES, in seconds)
  const p=await b.newPage({viewport:{width:1920,height:1300}});await p.goto('file://'+__dirname+'/screens.html');
  await p.evaluate(()=>{document.getElementById('phone').style.display='none'});
  const tv=await p.$('#tv');
  for(const [a,z] of (process.env.RANGES||'27:33.4,38.8:44.2,105.5:110.6,121.2:126').split(',').map(r=>r.split(':').map(Number))){for(let i=Math.round(a*24);i<Math.round(z*24);i++){if(have(`${R}/tv24/${String(i).padStart(5,'0')}.png`))continue;await p.evaluate(t=>render(t),i/24);await tv.screenshot({path:`${R}/tv24/${String(i).padStart(5,'0')}.png`});}}
 } else if(mode==='ph24'){        // the phone alone, 24 a second, for the stretches laid over the plate of the phone (PHRANGES, in seconds)
  const p=await b.newPage({viewport:{width:1920,height:1300}});await p.goto('file://'+__dirname+'/screens.html');
  await p.evaluate(()=>{document.getElementById('tv').style.display='none'});
  const ps=await p.$('#ps');
  for(const [a,z] of (process.env.PHRANGES||'').split(',').filter(Boolean).map(r=>r.split(':').map(Number))){for(let i=Math.round(a*24);i<Math.round(z*24);i++){if(have(`${R}/ph24/${String(i).padStart(5,'0')}.png`))continue;await p.evaluate(t=>render(t),i/24);await ps.screenshot({path:`${R}/ph24/${String(i).padStart(5,'0')}.png`});}} } else if(mode==='caps'){
  const p=await b.newPage({viewport:{width:1920,height:1080}});await p.goto('file://'+__dirname+'/screens.html?mode=caps');
  const N=Math.round(T.EV.total*24);
  for(let i=+(process.env.FROM||0);i<Math.min(N,+(process.env.TO||N));i++){if(have(`${R}/caps/${String(i).padStart(5,'0')}.png`))continue;await p.evaluate(t=>render(t),i/24);await p.screenshot({path:`${R}/caps/${String(i).padStart(5,'0')}.png`,omitBackground:true});}
 }
 await b.close();
})();
