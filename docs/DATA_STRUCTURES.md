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

## Threading Model and Concurrency Safety

### Why Concurrent Modification Cannot Occur

The `WebDavFile` children collection is accessed exclusively through Android's `DocumentsProvider` 
framework, which uses a specific threading model:

1. **Binder Thread Access**: All `DocumentsProvider` methods (`queryChildDocuments`, `createDocument`, 
   `deleteDocument`, etc.) are called on Binder threads by the Android framework.

2. **Synchronous Execution**: Each method uses `runBlocking` to block until completion:
   ```kotlin
   val res = runBlocking(Dispatchers.IO) {
       clients.get(account).propFind(WebDavPath(parentPath, true))
   }
   // Iteration happens AFTER the blocking call completes
   for (file in parentFile.children()) { ... }
   ```

3. **Sequential Request Handling**: The DocumentsProvider framework serializes requests for the 
   same document/directory, meaning one operation completes before another begins.

4. **Read-Only Iteration Pattern**: In the current codebase (as of December 2024), all iteration 
   over `children()` only **reads** the collection (e.g., calling `includeFile()`). No modifications 
   occur during iteration in the existing code paths.

### Comparison with Original ArrayList Implementation

The original `ArrayList`-based implementation had the **same concurrency characteristics**:
- No synchronization was used
- Iteration was not modified during traversal in existing code paths
- The framework's threading model prevented concurrent access

The switch to `LinkedHashMap` maintains this safety model. The 
`ConcurrentModificationException` warning in the documentation is a defensive best-practice 
note. Under the current threading model and usage patterns, it should not occur during 
normal operation.

### When to Use `childrenSnapshot()`

Use `childrenSnapshot()` if you need to:
1. Iterate over children while potentially adding/removing children in the same loop
2. Store a stable list that won't change if the parent is refreshed

In the current codebase (December 2024), `childrenSnapshot()` is not required because:
- `queryChildDocuments`: Iterates to populate UI cursor (read-only)
- `createDocument`: Adds a single child (no iteration)
- `deleteDocument`: Removes a single child (no iteration)

**Note:** If future code changes introduce iteration with modification, use `childrenSnapshot()` 
or the iterator's `remove()` method.

## Duplicate Path Handling (Overwrite Behavior)

### Behavior Change from ArrayList

**Original ArrayList behavior:**
- Calling `children.add(file)` twice with the same path would create **duplicate entries**
- This could lead to inconsistent state where the same file appears twice in directory listings

**New LinkedHashMap behavior:**
- Calling `addChild(file)` with an existing path **replaces** the old entry
- This is more appropriate behavior for a file system model where paths are unique

### Impact on "Pending" Files

When creating a file upload:
1. `createDocument` creates a "pending" file with `isPending = true`
2. If the path already exists (rare edge case), the old file is replaced
3. On upload success: The pending file is replaced with the real file
4. On upload failure: The pending file is removed via `removeChild()`

**Edge case analysis:**
- **Duplicate upload attempts:** If user uploads `file.txt` twice rapidly, the second pending 
  file replaces the first. This is a design decision: we show the most recent pending state.
- **Overwriting existing file:** Not a typical concern because:
  - `createDocument` is for **new** files (Android's file picker)
  - `openDocumentWrite` is for **existing** files (writes to existing entry)
- **Upload failure:** If upload fails, `removeChild(file)` removes only the pending file. 
  Since we store by path (not object identity), this correctly removes the pending entry.

**Improvement over original:** The original ArrayList approach could accumulate duplicate 
entries if the same file was uploaded multiple times without refresh. LinkedHashMap prevents 
this by design.

## Conclusion

`LinkedHashMap<Path, WebDavFile>` is the optimal choice for our access patterns because:

1. All production operations are O(1)
2. Insertion order is preserved for consistent UI
3. Simpler implementation with less code
4. Memory efficient
5. Immutable keys prevent cache corruption
6. Automatic duplicate handling prevents inconsistent state
7. Thread-safe under the DocumentsProvider threading model

The only trade-off is O(N) indexed access, which we don't use.
