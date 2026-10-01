import fr from '/home/user/FlowForge/frontend/src/i18n/messages/fr'
import en from '/home/user/FlowForge/frontend/src/i18n/messages/en'
import { createI18n } from 'vue-i18n'
const i18n=createI18n({legacy:false,locale:'fr',messages:{fr,en} as any,missingWarn:false,warnHtmlMessage:false})
function walk(o:any,p=''):[string,string][]{return Object.entries(o).flatMap(([k,v]:any)=>typeof v==='string'?[[p+k,v]]:walk(v,p+k+'.'))}
for (const [name,c] of [['fr',fr],['en',en]] as any) for (const [k,v] of walk(c)) {
  let err=''
  try{ i18n.global.locale.value=name; const p=new Proxy({},{get:()=>'x'}); i18n.global.t(k,p as any) }catch(e:any){err=String(e.message).split(String.fromCharCode(10))[0]}
  if(err) console.log(name,k,JSON.stringify(v),err)
  if(/\|/.test(v)) console.log('PIPE',name,k,v)
  if(/<[a-z]/i.test(v)) console.log('HTML',name,k,v)
}
console.log('done')
