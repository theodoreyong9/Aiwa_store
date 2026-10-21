const { chromium } = require('playwright');
(async()=>{
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});
 const p=await b.newPage({viewport:{width:1080,height:1920}});
 await p.goto('file://'+__dirname+'/screens.html');
 // the phone as a tall screen (the phone of the 3D scene is 0.0715 x 0.147 m): the UI is laid out on 540 x 1110
 await p.evaluate(()=>{const ph=document.getElementById('phone');ph.style.top='100px';ph.style.height='1250px';document.getElementById('ps').style.height='1110px';
  for(const id of ['table','cap','top','end','mail','sun'])document.getElementById(id).style.display='none';});
 const tv=await p.$('#scr'), ps=await p.$('#ps');
 const FPS=12,N=Math.round(122*FPS);
 for(let i=0;i<N;i++){const t=i/FPS;await p.evaluate(t=>render(t),t);const n=String(i+1).padStart(4,'0');
  await p.evaluate(()=>{document.getElementById('phone').style.visibility='hidden';document.getElementById('tv').style.visibility='visible'});
  await tv.screenshot({path:`/tmp/rfp/tvs/${n}.png`});
  await p.evaluate(()=>{document.getElementById('phone').style.visibility='visible';document.getElementById('tv').style.visibility='hidden'});await ps.screenshot({path:`/tmp/rfp/phs/${n}.png`});}
 await b.close();
})();
