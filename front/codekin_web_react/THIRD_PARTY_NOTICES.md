# Sources

Templates, editor panels, Tiptap toolbar, image helpers, sample data, fonts, rendering helpers, translations and shadcn UI components copied from Magic Resume 2.0.8 (https://github.com/JOYCEQL/magic-resume). Full license and additional restrictions: public/licenses/magic-resume.txt.

Changes: SectionWrapper selection moved from persisted store to optional React context; gallery application integrations adapted to Codekin. No section renderer/layout rewritten. Creation/editing now works with explicitly labelled browser-local drafts; server persistence requires subsequent backend APIs.

Editorial BaseInfo: added a number type guard for optional custom-field spacing to support strict TypeScript checking; visual spacing behavior unchanged.

Tailwind 4 migration: switched to the official Vite plugin and CSS theme configuration, retained the source v3 palette, migrated renamed shadow/blur/radius/outline/gradient utilities, and moved resets into the base cascade layer. Section rendering and template layouts remain from the source project.

Editor migration: adapted routing, account-scoped IndexedDB storage, React context, date inputs and shared field spacing. Removed AI, filesystem sync, GitHub credential inputs and server image proxy dependencies. Tiptap packages are unified at 3.31.4. The source license contains Apache 2.0 plus additional commercial-use restrictions; no MIT license is claimed for the migrated source.
