const { chromium } = require('playwright');
const fs=require('fs');
(async()=>{
 const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});
 const p=await b.newPage({viewport:{width:1920,height:1080}});
 p.on('pageerror',e=>console.log('ERR',e.message));
 await p.goto('file://'+__dirname+'/caps.html');
 fs.mkdirSync('/tmp/rfp/caps',{recursive:true});
 const FPS=24,N=122*FPS;
 const only=process.argv[2];
 for(let i=0;i<N;i++){ if(only&&i>+only)break; await p.evaluate(t=>render(t),i/FPS);await p.screenshot({path:`/tmp/rfp/caps/${String(i).padStart(5,'0')}.png`,omitBackground:true});}
 await b.close();
})();
