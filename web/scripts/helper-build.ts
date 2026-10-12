// YouCloud Helper (helper/) as the site hands it out: a Windows program, and for the Mac one
// program for both kinds of processor (Apple's and Intel's) in a zip that keeps it runnable. Go
// builds all of them from here, whatever this machine is; the Mac slices come signed ad hoc by
// Go's own linker, which an Apple processor needs to run them at all.
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { packZip } from './extension-zip'

export const HELPER_WINDOWS = 'youcloud-helper.exe'
export const HELPER_MAC = 'youcloud-helper-mac.zip'

/** Go: GO if set, else on PATH, else where its installer puts it. */
function goCommand(): string {
  if (process.env.GO) return process.env.GO
  for (const candidate of ['go', 'C:\\Program Files\\Go\\bin\\go.exe', '/usr/local/go/bin/go', '/opt/homebrew/bin/go']) {
    try {
      execFileSync(candidate, ['version'], { stdio: 'ignore' })
      return candidate
    } catch {
      // not this one
    }
  }
  throw new Error('YouCloud Helper is built with Go: install it (https://go.dev/dl, or winget install GoLang.Go) or set GO')
}

function build(dir: string, go: string, goos: string, goarch: string, out: string): Buffer {
  execFileSync(go, ['build', '-trimpath', '-buildvcs=false', '-ldflags=-s -w', '-o', out, '.'], {
    cwd: dir,
    env: { ...process.env, CGO_ENABLED: '0', GOOS: goos, GOARCH: goarch },
    stdio: ['ignore', 'ignore', 'inherit'],
  })
  return readFileSync(out)
}

/** A universal Mach-O: the fat header, then each slice at a 16 KiB boundary, as lipo lays it out. */
export function universal(slices: { cpu: number; subtype: number; data: Buffer }[]): Buffer {
  const ALIGN = 14
  const header = Buffer.alloc(8 + 20 * slices.length)
  header.writeUInt32BE(0xcafebabe, 0)
  header.writeUInt32BE(slices.length, 4)
  const parts: Buffer[] = [header]
  let at = header.length
  slices.forEach((s, i) => {
    const offset = Math.ceil(at / (1 << ALIGN)) * (1 << ALIGN)
    parts.push(Buffer.alloc(offset - at), s.data)
    header.writeUInt32BE(s.cpu, 8 + i * 20)
    header.writeUInt32BE(s.subtype, 12 + i * 20)
    header.writeUInt32BE(offset, 16 + i * 20)
    header.writeUInt32BE(s.data.length, 20 + i * 20)
    header.writeUInt32BE(ALIGN, 24 + i * 20)
    at = offset + s.data.length
  })
  return Buffer.concat(parts)
}

const CPU_X86_64 = 0x01000007
const CPU_ARM64 = 0x0100000c

export function buildHelper(dir: string): Record<string, Buffer> {
  const go = goCommand()
  const tmp = mkdtempSync(join(tmpdir(), 'youcloud-helper-'))
  try {
    const windows = build(dir, go, 'windows', 'amd64', join(tmp, 'win.exe'))
    const arm = build(dir, go, 'darwin', 'arm64', join(tmp, 'mac-arm64'))
    const intel = build(dir, go, 'darwin', 'amd64', join(tmp, 'mac-x64'))
    const mac = universal([
      { cpu: CPU_X86_64, subtype: 3, data: intel },
      { cpu: CPU_ARM64, subtype: 0, data: arm },
    ])
    return {
      [HELPER_WINDOWS]: windows,
      [HELPER_MAC]: packZip([{ name: 'YouCloud Helper', data: mac, mode: 0o755 }]),
    }
  } finally {
    rmSync(tmp, { recursive: true, force: true })
  }
}

/** When the helper's sources last changed: the dev server builds again only then. */
export function helperStamp(dir: string): number {
  return Math.max(
    ...readdirSync(dir)
      .filter((f) => /\.(go|mod|sum|json)$/.test(f))
      .map((f) => statSync(join(dir, f)).mtimeMs),
  )
}

export const hasHelper = (dir: string) => existsSync(join(dir, 'go.mod'))
