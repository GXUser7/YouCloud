// The extension as a zip for the site to hand out (/youcloud-extension.zip): the folder's files at
// the archive's root, deflated, as Chrome takes a zip dropped on chrome://extensions and as
// Windows' «Извлечь всё» unpacks into a folder of its own.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { crc32, deflateRawSync } from 'node:zlib'

export const EXTENSION_ZIP = 'youcloud-extension.zip'

// A fixed date in every entry: the same files give the same archive.
const DOS_TIME = 0
const DOS_DATE = ((2026 - 1980) << 9) | (1 << 5) | 1

function filesOf(dir: string, root = dir): string[] {
  return readdirSync(dir)
    .sort()
    .flatMap((name) => {
      const path = join(dir, name)
      return statSync(path).isDirectory() ? filesOf(path, root) : [relative(root, path).replaceAll('\\', '/')]
    })
}

export function extensionVersion(dir: string): string {
  return JSON.parse(readFileSync(join(dir, 'manifest.json'), 'utf8')).version
}

export function packExtension(dir: string): Buffer {
  const parts: Buffer[] = []
  const central: Buffer[] = []
  let offset = 0
  const names = filesOf(dir)
  for (const name of names) {
    const data = readFileSync(join(dir, name))
    const packed = deflateRawSync(data, { level: 9 })
    const path = Buffer.from(name, 'utf8')
    const crc = crc32(data)

    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4) // version needed: 2.0
    local.writeUInt16LE(0x0800, 6) // names in UTF-8
    local.writeUInt16LE(8, 8) // deflate
    local.writeUInt16LE(DOS_TIME, 10)
    local.writeUInt16LE(DOS_DATE, 12)
    local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(packed.length, 18)
    local.writeUInt32LE(data.length, 22)
    local.writeUInt16LE(path.length, 26)
    parts.push(local, path, packed)

    const entry = Buffer.alloc(46)
    entry.writeUInt32LE(0x02014b50, 0)
    entry.writeUInt16LE(20, 4) // made by
    entry.writeUInt16LE(20, 6) // version needed
    entry.writeUInt16LE(0x0800, 8)
    entry.writeUInt16LE(8, 10)
    entry.writeUInt16LE(DOS_TIME, 12)
    entry.writeUInt16LE(DOS_DATE, 14)
    entry.writeUInt32LE(crc, 16)
    entry.writeUInt32LE(packed.length, 20)
    entry.writeUInt32LE(data.length, 24)
    entry.writeUInt16LE(path.length, 28)
    entry.writeUInt32LE(offset, 42)
    central.push(entry, path)

    offset += local.length + path.length + packed.length
  }
  const directory = Buffer.concat(central)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0)
  end.writeUInt16LE(names.length, 8)
  end.writeUInt16LE(names.length, 10)
  end.writeUInt32LE(directory.length, 12)
  end.writeUInt32LE(offset, 16)
  return Buffer.concat([...parts, directory, end])
}
