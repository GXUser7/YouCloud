import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

// Actual page-side bridge: old hosts must not advertise a write flow they cannot perform.
const source=readFileSync(new URL('../src/lib/extension.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
const listeners=new Map()
const page={postMessage(){}}
const origin='https://youcloud.inkerror67.workers.dev'
const exports={}
vm.runInNewContext(compiled,{exports,require:(id)=>{
 if(id.includes('i18n'))return{tr:s=>s}
 if(id.endsWith('manifest.json'))return{version:'0.9.2'}
 if(id.endsWith('version.json'))return{version:'1.1.0'}
 throw new Error(id)
},addEventListener:(type,fn)=>listeners.set(type,fn),window:page,location:{origin},document:{documentElement:{dataset:{}}},setTimeout,clearTimeout,Response,URL,Uint8Array})
function hello(data,source=page,from=origin){listeners.get('message')({source,origin:from,data:{yc:'ext',kind:'hello',...data}})}
hello({version:'0.8.0'})
assert.equal(exports.soundCloudWrites(),false)
hello({version:'0.9.2'})
assert.equal(exports.soundCloudWrites(),true,'extension 0.9 implements same-page writes')
hello({version:'0.10.0',host:'masaki'})
assert.equal(exports.soundCloudWrites(),false,'old Masaki has no page-write operation')
hello({version:'0.11.0',host:'masaki',capabilities:['sc-page-writes']})
assert.equal(exports.soundCloudWrites(),true,'updated Masaki explicitly advertises the same contract')
hello({version:'0.11.0',host:'masaki'})
assert.equal(exports.soundCloudWrites(),false,'a version alone is not a capability declaration')
hello({version:'0.11.0',host:'masaki',capabilities:['sc-page-writes']},{},origin)
assert.equal(exports.soundCloudWrites(),false,'foreign sources cannot advertise a host capability')
console.log('Bridge capability negotiation: extension, updated/old Masaki and foreign sources passed')
