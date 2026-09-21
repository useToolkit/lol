import { checkClient, normalizeAround, opinet, send, validateFuel, wgsToKatec } from "./_lib.js";

export default async function handler(req,res){
  if(req.method!=="GET") return send(res,405,{error:"METHOD_NOT_ALLOWED"});
  if(!checkClient(req)) return send(res,401,{error:"UNAUTHORIZED"});
  try{
    let lat=Number(req.query.lat),lng=Number(req.query.lng);
    if(!Number.isFinite(lat)||!Number.isFinite(lng)||lat<33||lat>39.5||lng<124||lng>132) return send(res,400,{error:"INVALID_LOCATION"});
    lat=Math.round(lat*1000)/1000; lng=Math.round(lng*1000)/1000;
    const fuel=validateFuel(req.query.fuel),[x,y]=wgsToKatec(lat,lng);
    const j=await opinet("aroundAll.do",{x:x.toFixed(3),y:y.toFixed(3),radius:5000,sort:1,prodcd:fuel});
    const oils=j?.RESULT?.OIL||[];
    const stations=(Array.isArray(oils)?oils:[oils]).map(normalizeAround).filter(s=>s.id&&s.price>0);
    return send(res,200,{fuel,location:{lat,lng,precision:"~110m"},radius:5000,stations,updatedAt:new Date().toISOString()});
  }catch(e){
    const config=String(e?.message||"").startsWith("SERVER_CONFIG_");
    return send(res,config?503:502,{error:config?"SERVER_NOT_CONFIGURED":"UPSTREAM_ERROR"});
  }
}
