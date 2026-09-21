import { checkClient, katecToWgs, opinet, send, validateFuel } from "./_lib.js";

export default async function handler(req,res){
  if(req.method!=="GET") return send(res,405,{error:"METHOD_NOT_ALLOWED"});
  if(!checkClient(req)) return send(res,401,{error:"UNAUTHORIZED"});
  const id=String(req.query.id||"").trim();
  if(!/^[A-Za-z0-9_-]{2,40}$/.test(id)) return send(res,400,{error:"INVALID_ID"});
  try{
    const fuel=validateFuel(req.query.fuel),j=await opinet("detailById.do",{id});
    const raw=Array.isArray(j?.RESULT?.OIL)?j.RESULT.OIL[0]:j?.RESULT?.OIL;
    if(!raw) return send(res,404,{error:"NOT_FOUND"});
    const prices=Array.isArray(raw.OIL_PRICE)?raw.OIL_PRICE:[raw.OIL_PRICE].filter(Boolean);
    const p=prices.find(x=>x?.PRODCD===fuel);
    const x=Number(raw.GIS_X_COOR||0),y=Number(raw.GIS_Y_COOR||0),ll=(x&&y)?katecToWgs(x,y):[0,0];
    return send(res,200,{station:{id:raw.UNI_ID||id,name:raw.OS_NM||"",brand:raw.POLL_DIV_CD||raw.POLL_DIV_CO||"",address:raw.NEW_ADR||raw.VAN_ADR||"",price:Number(p?.PRICE||0),lat:ll[0],lng:ll[1]},updatedAt:new Date().toISOString()});
  }catch(e){
    const config=String(e?.message||"").startsWith("SERVER_CONFIG_");
    return send(res,config?503:502,{error:config?"SERVER_NOT_CONFIGURED":"UPSTREAM_ERROR"});
  }
}
