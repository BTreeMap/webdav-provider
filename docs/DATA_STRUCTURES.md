# Data Structures for WebDavFile Children Collection

This document analyzes the data structure options for storing child files in `WebDavFile` and justifies the choice of `LinkedHashMap`.

## Requirements Analysis

Based on the codebase analysis, `WebDavFile.children` is accessed in the following ways:

| Operation | Location | Frequency |
|-----------|----------|-----------|
| **Add child** | `WebDavClient.kt`, `WebDavProvider.kt` | Every directory listing, file creation |
| **Remove child** | `WebDavProvider.kt` | File deletion, move operations |
| **Iterate all children** | `WebDavProvider.kt` | Directory listings in UI |
| **Lookup by path** | `WebDavCache.kt`, `WebDavProvider.kt` | Cache validation, document resolution |
| **Check existence** | `WebDavCache.kt` | Cache validation |

Notably, **indexed access** (e.g., `children[5]`) is **NOT** used in production code.

## Data Structure Comparison

### Option 1: ArrayList Only

```kotlin
private val children: MutableList<WebDavFile> = ArrayList()
```

| Operation | Complexity |
|-----------|------------|
| Add (at end) | O(1) amortized |
| Remove by reference | **O(N)** - must search then shift |
| Lookup by path | **O(N)** - linear search |
| Iterate | O(N) |
| Index access | O(1) |

**Verdict:** Poor for lookup-heavy workloads. Path lookups during cache validation become O(N).

### Option 2: ArrayList + HashMap (Previous Implementation)

```kotlin
private val childrenList: MutableList<WebDavFile> = ArrayList()
private val childrenByPath: MutableMap<Path, WebDavFile> = HashMap()
```

| Operation | Complexity |
|-----------|------------|
| Add (at end) | O(1) |
| Remove by reference | **O(N)** - ArrayList still shifts |
| Lookup by path | O(1) |
| Iterate | O(N) |
| Index access | O(1) |

**Verdict:** Good lookup performance, but:
- Requires wrapper class to keep both structures in sync
- O(N) removal due to ArrayList shifting
- Two data structures = more memory, more complexity

### Option 3: LinkedHashMap (Current Implementation) ✓

```kotlin
private val childrenMap: LinkedHashMap<Path, WebDavFile> = LinkedHashMap()
```

| Operation | Complexity |
|-----------|------------|
| Add | O(1) |
| Remove by key | O(1) |
| Lookup by path | O(1) |
| Iterate | O(N) - insertion order preserved |
| Index access | **O(N)** - not directly supported |

**Verdict:** Optimal for our access patterns:
- All operations we actually use are O(1)
- Insertion order preserved for consistent UI display
- Single data structure = simpler code, less memory
- No wrapper classes needed

### Option 4: TreeMap

```kotlin
private val childrenMap: TreeMap<Path, WebDavFile> = TreeMap()
```

| Operation | Complexity |
|-----------|------------|
| Add | O(log N) |
| Remove by key | O(log N) |
| Lookup by path | O(log N) |
| Iterate | O(N) - sorted order |

**Verdict:** Slower than HashMap. We don't need sorted order (insertion order is fine).

## Why LinkedHashMap is Optimal

1. **O(1) for all operations we use:**
   - Adding children (directory listings)
   - Removing children (deletions)
   - Path-based lookups (cache validation)

2. **Insertion order preservation:**
   - Children appear in the order they were added
   - Consistent UI behavior for directory listings

3. **Simple implementation:**
   - No wrapper classes needed
   - Single data structure to maintain
   - Clean, idiomatic Kotlin API

4. **Memory efficient:**
   - One LinkedHashMap vs ArrayList + HashMap
   - LinkedHashMap uses ~same memory as HashMap + linked list overhead

## API Design

The new API explicitly reflects the data structure choice:

```kotlin
// O(1) operations
fun findChildByPath(path: Path): WebDavFile?
fun containsChild(path: Path): Boolean
fun addChild(child: WebDavFile)
fun removeChild(child: WebDavFile): Boolean
fun removeChildByPath(path: Path): WebDavFile?
fun clearChildren()

// Properties
val childCount: Int
fun hasChildren(): Boolean

// Iteration (O(N), preserves insertion order)
fun children(): Collection<WebDavFile>
fun childrenIterator(): MutableIterator<WebDavFile>
fun childrenSnapshot(): List<WebDavFile>  // Safe for modification during iteration
```

## Path Immutability

The `path` property is now immutable (`val` instead of `var`) to prevent cache corruption:

```kotlin
class WebDavFile(
    val path: Path,  // Immutable - ensures HashMap key stability
    ...
)
```

This is critical because `LinkedHashMap` uses `path` as the key. If the path changed after insertion, the file would become "lost" in the map under the old hash.

## Performance Characteristics

For a typical directory with N children:

| Operation | Time | Memory |
|-----------|------|--------|
| List directory | O(N) network + O(N) insertion | O(N) |
| Find file in directory | O(1) | - |
| Delete file | O(1) | - |
| Cache validation | O(1) lookup | - |

## Conclusion

`LinkedHashMap<Path, WebDavFile>` is the optimal choice for our access patterns because:

1. All production operations are O(1)
2. Insertion order is preserved for consistent UI
3. Simpler implementation with less code
4. Memory efficient
5. Immutable keys prevent cache corruption

The only trade-off is O(N) indexed access, which we don't use.
