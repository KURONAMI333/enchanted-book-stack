# Enchanted Book Stack

Stack up to 54 enchanted books into a single portable item. Open it like a chest, nest it inside shulker boxes or backpacks, and reclaim the inventory slots your enchanting library has been eating.

A working set of enchanted books is 54 slots — move them between bases or carry them into the Nether and they fragment across half your storage. Enchanted Book Stack holds dozens in one slot, shows what's inside on hover, and drops into a shulker box like any other item.

**Features**

- 54 enchanted books per stack, in a vanilla 9×6 large-chest grid
- Bundle-style stashing: hold the stack and right-click an enchanted book, or vice versa
- Nestable into shulker boxes, backpacks, ender chests, or another mod's container
- Hover preview of the first five contained enchantments, vanilla shulker-style
- Always glints, like an enchanted book should
- Enchanted books only by design — it won't accept regular books or container items

It stores its contents in vanilla's own `DataComponents.CONTAINER` — the same component bundles use — so there's no custom NBT, no mixin, and no shared channels. The stack is an ordinary single-slot item: drop it, hopper it, or pipe it through a logistics network and it just works. It can't be nested inside another Enchanted Book Stack (anti-recursion). v0.1 keeps the grid in insertion order — no search or sort yet.

Acquire it from an Expert-level librarian (24 emerald + 4 leather), or in creative.

**Dependencies**

- Fabric only: Fabric API (the NeoForge and Forge builds need nothing)

Install on the server and on each client.

Free to use in any modpack. Source and issues: https://github.com/KURONAMI333/enchanted-book-stack
