// The actual catalog/service transforms and favorite queue entry points, with API doubles.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
const catalog = { tracks: new Map(), sets: new Map(), artists: new Map(), colorFor: () => '#888', compact: String, countWords: () => '', putArtist: () => '', putTrack: t => (catalog.tracks.set(t.id,t),t.id), putSet: s => (catalog.sets.set(s.id,s),s.id) }
const tr = (s,...a)=>s.replace(/%s/g,()=>String(a.shift()))
function moduleFrom(path,imports,extra='',globals={}) {
 const src=readFileSync(new URL(path,import.meta.url),'utf8').replaceAll('import.meta.env.DEV','false')+'\n'+extra
 const js=ts.transpileModule(src,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
 const exports={}
 vm.runInNewContext(js,{exports,require:id=>{assert.ok(id in imports,`unknown import ${id}`);return imports[id]},URL,URLSearchParams,Response,Headers,Request,localStorage:{getItem:()=>null,setItem(){}},setTimeout,clearTimeout,console,...globals})
 return exports
}
const entries=[
 {video_id:'live1234567',title:'Live',badges:[{metadataBadgeRenderer:{style:'BADGE_STYLE_TYPE_LIVE_NOW'}}]},
 {video_id:'song1234567',title:'Song'},
 {video_id:'noLen123456',title:'Missing length'},
]
const client={music:{getPlaylist:async()=>({items:entries,header:{title:'Liked'},has_continuation:false})},actions:{execute:async()=>({data:{queueDatas:[{content:{playlistPanelVideoRenderer:{videoId:'song1234567',lengthText:{simpleText:'3:12'}}}}]}})}}
const youtube=moduleFrom('../src/lib/services/youtube.ts',{
 '../i18n.svelte':{tr,i18n:{lang:'ru'}},'../catalog':catalog,'../accounts.svelte':{accounts:{yt:true}},'../extension':{},'../net':{},'youtubei.js/web':{Platform:{shim:{}},UniversalCache:class{},Innertube:{create:async()=>client}},
},'export {putYtTrack as testPut, liveStatus as testLive}')
assert.equal(youtube.testLive({duration:{seconds:0}}),undefined,'missing duration is not proof of live')
assert.equal(youtube.testLive({is_live:false}),false)
assert.equal(youtube.testLive({thumbnailOverlays:[{thumbnailOverlayTimeStatusRenderer:{style:'LIVE'}}]}),true)
youtube.testPut(entries[0]);youtube.testPut({...entries[0],badges:undefined})
assert.equal(catalog.tracks.get('yt:t:live1234567').live,true,'revisiting a shelf must retain known live metadata')
const set=await youtube.loadSet('yt:pl:LM')
assert.equal(set.kind,'liked','direct liked-list URLs must use favorite filtering')
assert.equal(catalog.tracks.get('yt:t:song1234567').duration,192,'metadata fills before liked-list resolves')
assert.notEqual(catalog.tracks.get('yt:t:noLen123456').live,true,'unknown duration remains a song')
const favorite=moduleFrom('../src/lib/favorites.svelte.ts',{
 '../i18n.svelte':{tr},'../catalog':catalog,'./supabase':{},'./catalog':catalog,'./i18n.svelte':{tr},'./supabase':{supabase:{auth:{onAuthStateChange(){}}}},
},'export {put as testPut, rowOf as testRow}',{$state:x=>x,addEventListener(){}})
favorite.testPut({service:'youtube',track_id:'song1234567',live:true})
assert.equal(catalog.tracks.get('yt:t:song1234567').live,true,'phone favorite metadata updates an existing search track')
assert.equal(favorite.testRow(catalog.tracks.get('yt:t:song1234567'),true).live,true,'live survives save to another device')
// Evaluate the real queue helpers; no audio/browser globals needed for this boundary.
const source=readFileSync(new URL('../src/lib/state.svelte.ts',import.meta.url),'utf8')
const start=source.indexOf('export const likedTracks')
const end=source.indexOf('// A mix or station',start)
const fragment=source.slice(start,end)+'\nexport const radioForSet = () => null'
const js=ts.transpileModule(fragment,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
const state={exports:{},app:{showLiveInLiked:false},trackOf:id=>catalog.tracks.get(id),live:{loadSet:async()=>set},playQueue:(...args)=>state.queue=args,toast:()=>{},reason:String,tr}
vm.runInNewContext(js,state)
await state.exports.playSet('yt:pl:LM')
assert.deepEqual(Array.from(state.queue[0]),['yt:t:noLen123456'])
assert.match(state.queue[3],/:songs$/)
state.app.showLiveInLiked=true
await state.exports.playSet('yt:pl:LM',true)
assert.equal(state.queue[0].length,3)
assert.match(state.queue[3],/:live$/,'changing preference must not resume a stale filtered queue')
console.log('Live favorites: metadata, save/restore, missing lengths, direct playlist and filtered playback passed')

