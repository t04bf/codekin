import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'
import { compile } from 'json-schema-to-typescript'
import Ajv from 'ajv/dist/2020.js'

const front = fileURLToPath(new URL('../', import.meta.url))
const root = path.resolve(front, '../..')
const schema = JSON.parse(await readFile(path.join(root, 'contracts/resume/resume-document.schema.json'), 'utf8'))
const blank = JSON.parse(await readFile(path.join(root, 'contracts/resume/blank.json'), 'utf8'))
const example = JSON.parse(await readFile(path.join(root, 'contracts/resume/example.json'), 'utf8'))
const validate = new Ajv().compile(schema)
for (const document of [blank, example]) if (!validate(document)) throw new Error(JSON.stringify(validate.errors))
// Read the registered renderer configs; no parallel legacy catalog is maintained.
const registry = await readFile(path.join(front, 'src/config/templates.ts'), 'utf8')
const imports = [...registry.matchAll(/import\s*\{\s*(\w+)\s*\}\s*from\s*"@\/components\/templates\/([^\"]+)\/config"/g)]
const catalog = []
for (const [index, [, exportName, key]] of imports.entries()) {
  const source = await readFile(path.join(front, `src/components/templates/${key}/config.ts`), 'utf8')
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
  const template = (await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`))[exportName]
  const document = structuredClone(blank)
  Object.assign(document.layout.globalSettings, {
    themeColor: template.colorScheme.primary, pagePadding: template.spacing.contentPadding,
    sectionSpacing: template.spacing.sectionGap, paragraphSpacing: template.spacing.itemGap,
  })
  document.layout.basic.layout = template.basic.layout
  if (!validate(document)) throw new Error(JSON.stringify(validate.errors))
  catalog.push({ id: String(910000000000000001n + BigInt(index)), templateKey: template.id,
    templateVersion: 1, schemaVersion: 1, name: template.name, description: template.description,
    status: 'PUBLISHED', sortOrder: (index + 1) * 10, defaultContent: document.content, defaultLayout: document.layout })
}
if (!catalog.length || new Set(catalog.map(template => template.templateKey)).size !== catalog.length) throw new Error('Invalid template registry')
const types = await compile(schema, 'ResumeDocument', { bannerComment: '/* Generated from contracts/resume/resume-document.schema.json. Do not edit. */', ignoreMinAndMaxItems: true })
for (const [file, expected] of [
  [path.join(front, 'src/api/resume-document.generated.ts'), types],
  [path.join(front, 'src/features/resume/data/templates.json'), JSON.stringify(catalog, null, 2) + '\n'],
]) {
  if (process.argv.includes('--check')) { if (await readFile(file, 'utf8') !== expected) throw new Error(`Contract output differs: ${file}`) }
  else await writeFile(file, expected)
}
console.log(`Verified resume schema, TypeScript types and ${catalog.length} template defaults`)
await import('./generate-resume-sql.mjs')
