const OPINET_BASE = "https://www.opinet.co.kr/api";

const WGS_A = 6378137.0, WGS_RF = 298.257223563;
const BES_A = 6377397.155, BES_RF = 299.1528128;
const DX = -115.80, DY = 474.99, DZ = 674.11;
const RX = (1.16 / 3600) * Math.PI / 180;
const RY = (-2.31 / 3600) * Math.PI / 180;
const RZ = (-1.63 / 3600) * Math.PI / 180;
const SCALE = 6.43e-6;
const LAT0 = 38 * Math.PI / 180, LON0 = 128 * Math.PI / 180;
const K0 = 0.9999, X0 = 400000, Y0 = 600000;

function geodeticToXyz(latDeg, lonDeg, h, a, rf) {
  const lat=latDeg*Math.PI/180, lon=lonDeg*Math.PI/180;
  const f=1/rf, e2=f*(2-f), sin=Math.sin(lat), cos=Math.cos(lat);
  const n=a/Math.sqrt(1-e2*sin*sin);
  return [(n+h)*cos*Math.cos(lon),(n+h)*cos*Math.sin(lon),(n*(1-e2)+h)*sin];
}
function xyzToGeodetic(x,y,z,a,rf) {
  const f=1/rf,e2=f*(2-f),lon=Math.atan2(y,x),p=Math.hypot(x,y);
  let lat=Math.atan2(z,p*(1-e2)),h=0;
  for(let i=0;i<15;i++){const sin=Math.sin(lat),n=a/Math.sqrt(1-e2*sin*sin);h=p/Math.cos(lat)-n;const next=Math.atan2(z,p*(1-e2*n/(n+h)));if(Math.abs(next-lat)<1e-14){lat=next;break;}lat=next;}
  return [lat*180/Math.PI,lon*180/Math.PI,h];
}
function forwardHelmert(x,y,z){const m=1+SCALE;return [DX+m*x-RZ*y+RY*z,DY+RZ*x+m*y-RX*z,DZ-RY*x+RX*y+m*z];}
function inverseHelmert(x2,y2,z2){
  const m=1+SCALE,a=[[m,-RZ,RY,x2-DX],[RZ,m,-RX,y2-DY],[-RY,RX,m,z2-DZ]];
  for(let i=0;i<3;i++){let p=i;for(let r=i+1;r<3;r++)if(Math.abs(a[r][i])>Math.abs(a[p][i]))p=r;[a[i],a[p]]=[a[p],a[i]];const d=a[i][i];for(let c=i;c<4;c++)a[i][c]/=d;for(let r=0;r<3;r++)if(r!==i){const f=a[r][i];for(let c=i;c<4;c++)a[r][c]-=f*a[i][c];}}
  return [a[0][3],a[1][3],a[2][3]];
}
function meridionalArc(phi,a,e2){return a*((1-e2/4-3*e2*e2/64-5*e2**3/256)*phi-(3*e2/8+3*e2*e2/32+45*e2**3/1024)*Math.sin(2*phi)+(15*e2*e2/256+45*e2**3/1024)*Math.sin(4*phi)-(35*e2**3/3072)*Math.sin(6*phi));}
function tmForward(latDeg,lonDeg){
  const f=1/BES_RF,e2=f*(2-f),ep2=e2/(1-e2),lat=latDeg*Math.PI/180,lon=lonDeg*Math.PI/180;
  const sin=Math.sin(lat),cos=Math.cos(lat),tan=Math.tan(lat),n=BES_A/Math.sqrt(1-e2*sin*sin),t=tan*tan,c=ep2*cos*cos,aa=(lon-LON0)*cos;
  const m=meridionalArc(lat,BES_A,e2),m0=meridionalArc(LAT0,BES_A,e2);
  const x=X0+K0*n*(aa+(1-t+c)*aa**3/6+(5-18*t+t*t+72*c-58*ep2)*aa**5/120);
  const y=Y0+K0*(m-m0+n*tan*(aa*aa/2+(5-t+9*c+4*c*c)*aa**4/24+(61-58*t+t*t+600*c-330*ep2)*aa**6/720));
  return [x,y];
}
function tmInverse(x,y){
  const f=1/BES_RF,e2=f*(2-f),ep2=e2/(1-e2),m0=meridionalArc(LAT0,BES_A,e2),m1=m0+(y-Y0)/K0;
  const mu=m1/(BES_A*(1-e2/4-3*e2*e2/64-5*e2**3/256)),e1=(1-Math.sqrt(1-e2))/(1+Math.sqrt(1-e2));
  const fp=mu+(3*e1/2-27*e1**3/32)*Math.sin(2*mu)+(21*e1*e1/16-55*e1**4/32)*Math.sin(4*mu)+(151*e1**3/96)*Math.sin(6*mu)+(1097*e1**4/512)*Math.sin(8*mu);
  const sin=Math.sin(fp),cos=Math.cos(fp),tan=Math.tan(fp),c1=ep2*cos*cos,t1=tan*tan,n1=BES_A/Math.sqrt(1-e2*sin*sin),r1=BES_A*(1-e2)/(1-e2*sin*sin)**1.5,d=(x-X0)/(n1*K0);
  const lat=fp-(n1*tan/r1)*(d*d/2-(5+3*t1+10*c1-4*c1*c1-9*ep2)*d**4/24+(61+90*t1+298*c1+45*t1*t1-252*ep2-3*c1*c1)*d**6/720);
  const lon=LON0+(d-(1+2*t1+c1)*d**3/6+(5-2*c1+28*t1-3*c1*c1+8*ep2+24*t1*t1)*d**5/120)/cos;
  return [lat*180/Math.PI,lon*180/Math.PI];
}
export function wgsToKatec(lat,lon){const xyz=geodeticToXyz(lat,lon,0,WGS_A,WGS_RF),b=inverseHelmert(...xyz),ll=xyzToGeodetic(...b,BES_A,BES_RF);return tmForward(ll[0],ll[1]);}
export function katecToWgs(x,y){const ll=tmInverse(x,y),b=geodeticToXyz(ll[0],ll[1],0,BES_A,BES_RF),w=forwardHelmert(...b),out=xyzToGeodetic(...w,WGS_A,WGS_RF);return [out[0],out[1]];}

export function validateFuel(v){return v==="D047"?"D047":"B027";}
export function requireEnv(name){const v=process.env[name];if(!v)throw new Error("SERVER_CONFIG_"+name);return v;}
export function checkClient(req){
  const expected=process.env.FUELPICK_CLIENT_TOKEN;
  if(!expected) return true;
  const h=req.headers.authorization||"";
  return h===`Bearer ${expected}`;
}
export async function opinet(path, params){
  const key=requireEnv("OPINET_API_KEY");
  const u=new URL(OPINET_BASE+"/"+path);
  Object.entries({...params,out:"json",certkey:key}).forEach(([k,v])=>u.searchParams.set(k,String(v)));
  const ctl=new AbortController();const timer=setTimeout(()=>ctl.abort(),7000);
  try{
    const r=await fetch(u,{headers:{"Accept":"application/json","User-Agent":"FuelPick-Server/1.0"},signal:ctl.signal});
    if(!r.ok)throw new Error("OPINET_HTTP_"+r.status);
    return await r.json();
  } finally { clearTimeout(timer); }
}
export function send(res,status,body){
  res.status(status).setHeader("Content-Type","application/json; charset=utf-8");
  res.setHeader("Cache-Control",status===200?"public, s-maxage=300, stale-while-revalidate=60":"no-store");
  res.end(JSON.stringify(body));
}
export function normalizeAround(o){
  const x=Number(o.GIS_X_COOR||0),y=Number(o.GIS_Y_COOR||0),ll=(x&&y)?katecToWgs(x,y):[0,0];
  return {id:o.UNI_ID||"",name:o.OS_NM||"",brand:o.POLL_DIV_CD||o.POLL_DIV_CO||"",price:Number(o.PRICE||0),distance:Number(o.DISTANCE||0),lat:ll[0],lng:ll[1]};
}
