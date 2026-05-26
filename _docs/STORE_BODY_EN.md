# Enchanted Book Stack

> Stack up to **54 enchanted books** into a single portable item. Open it like a chest, nest it inside shulker boxes / backpacks, and reclaim the slots your enchanting library has been eating.

Anyone who hoards enchanted books knows the pain: 54 enchanted books = 54 slots in your inventory. Move them between bases, dump them in a chest "temporarily", or carry a working set into the Nether, and they fragment into half your storage. Enchanted Book Stack is the one bundle-shaped fix: hold dozens of enchanted books in a single inventory slot, look at the icon to read what's inside, drop the stack into a shulker box and forget about it.

- 📚 **54 enchanted books per stack** (vanilla large chest UI, 9×6)
- ✋ **Bundle-style stash** — hold the stack and right-click an enchanted book, or vice versa
- 📦 **Nestable** — drop the stack into shulker boxes, backpacks, or other containers
- 🔍 **Hover preview** — shows the first 5 contained enchantments, vanilla shulker-style
- ✨ Always **glints**, like an enchanted book should
- 🛍️ Acquirable via **librarian trade** (Expert level, 24 emerald + 4 leather)
- 🚫 **Enchanted books only** — by design, won't accept regular books or container items

## What it does / Usage

1. Trade with an Expert-level librarian, or craft (recipe TBD by your modpack)
2. Right-click in the air → opens the 54-slot grid
3. Or hold the stack and right-click an enchanted book in your inventory to stash it
4. Hover over the stack to see what's inside (vanilla shulker preview)

## Supported loaders / versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.21.1 | ✅ | ✅ | ✅ |

Ships for NeoForge, Forge, and Fabric on Minecraft 1.21.1. (Minecraft 1.20.1 is not supported — the mod relies on the 1.21+ `DataComponents.CONTAINER` data component for storage.)

## Dependencies

- **Fabric build**: Fabric API
- NeoForge / Forge builds: none

## Compatibility & scope

The stack stores its contents in vanilla's own `DataComponents.CONTAINER`, the same component used by vanilla bundles. No custom NBT, no mixin, no shared inventory channels, no SavedData. The stack is just an item — drop it on the ground, throw it in a hopper, pipe it through a mod's logistics network, it all works.

It's also nestable: a stack can sit inside a shulker box, a backpack, an ender chest, or even another mod's container, since it's an ordinary single-slot item with NBT.

## Known limitations

- v0.1 doesn't search / sort the contents — the grid is in insertion order (vanilla large-chest behaviour)
- No client-side filtering or hierarchical browsing (those were earlier design experiments and intentionally cut)
- The stack itself cannot be put inside another Enchanted Book Stack (anti-recursion)

## Install

1. Install your mod loader — NeoForge, Forge, or Fabric — for Minecraft 1.21.1
2. Drop `enchantedbookstack-0.1.0.jar` (loader-matching build) into `mods/`
3. Trade with a librarian or use creative

- Minecraft 1.21.1 · JDK 21

## Languages

English, Japanese.

## License

MIT — modpack inclusion welcome, no credit required.

Author: KURONAMI
