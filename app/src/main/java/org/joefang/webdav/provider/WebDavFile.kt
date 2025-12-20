package org.joefang.webdav.provider

import android.webkit.MimeTypeMap
import com.thegrizzlylabs.sardineandroid.model.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a file or directory on a WebDAV server.
 * 
 * This class maintains a tree structure of files where directories can have children.
 * 
 * ## Data Structure Choice: LinkedHashMap
 * 
 * Children are stored in a [LinkedHashMap] keyed by [Path], which provides:
 * - **O(1) lookup** by path via [findChildByPath] or [containsChild]
 * - **O(1) insertion** at the end (preserves insertion order)
 * - **O(1) deletion** by key/path
 * - **Ordered iteration** in insertion order (important for consistent UI display)
 * 
 * This is optimal for our access patterns which require:
 * - Fast path-based lookups (cache validation, document resolution)
 * - Adding children when listing directories
 * - Removing children when files are deleted
 * - Iterating over children for directory listings
 * 
 * We do NOT require indexed access (e.g., `children[5]`), so LinkedHashMap is ideal.
 * See `docs/DATA_STRUCTURES.md` for detailed analysis.
 * 
 * @property path The immutable path of this file. Immutability ensures cache consistency.
 */
class WebDavFile(
    val path: Path,
    var isDirectory: Boolean = false,
    var contentType: String? = null,
    var isPending: Boolean = false
) {
    var parent: WebDavFile? = null
    
    /**
     * Children stored in a LinkedHashMap for O(1) lookup, insertion, and deletion.
     * Iteration preserves insertion order for consistent directory listings.
     */
    private val childrenMap: LinkedHashMap<Path, WebDavFile> = LinkedHashMap()
    
    val writable: Boolean = true

    var etag: String? = null
    var contentLength: Long? = null
    var quotaUsedBytes: Long? = null
    var quotaAvailableBytes: Long? = null

    var lastModified: Date? = null

    val name: String
        get() {
            if (path.fileName != null) {
                return path.fileName.toString()
            }

            return "/"
        }

    val davPath: WebDavPath
        get() = WebDavPath(path, isDirectory)

    val decodedName: String
        get() = URLDecoder.decode(name, StandardCharsets.UTF_8.name())

    constructor (res: Response, href: String = res.href)
            : this(Paths.get(href), res.propstat[0].prop.resourcetype.collection != null) {
        val prop = res.propstat[0].prop
        etag = prop.getetag
        contentType = parseContentType(name, prop.getcontenttype)
        contentLength = prop.getcontentlength?.toLongOrNull()
        quotaUsedBytes = prop.quotaUsedBytes?.content?.firstOrNull()?.toLongOrNull()
        quotaAvailableBytes = prop.quotaAvailableBytes?.content?.firstOrNull()?.toLongOrNull()
        lastModified = parseDate(prop.getlastmodified)
    }

    override fun toString(): String {
        return path.toString()
    }

    private fun parseDate(s: String?): Date? {
        if (s == null) {
            return s
        }

        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        return try {
            format.parse(s)
        } catch (e: ParseException) {
            null
        }
    }

    private fun parseContentType(fileName: String, contentType: String?): String {
        if (contentType != null) {
            return contentType
        }

        val ext = fileName.split(".").last()
        val res = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (res != null) {
            return res
        }

        return "application/octet-stream"
    }
    
    // ==================== Copy Method ====================
    
    /**
     * Creates a copy of this file with a new path.
     * All properties are copied except children (which are not transferred during rename).
     * 
     * @param newPath The path for the new file
     * @return A new WebDavFile with the same properties but a different path
     */
    fun copyWithNewPath(newPath: Path): WebDavFile {
        return WebDavFile(newPath, isDirectory, contentType, isPending).also {
            it.parent = parent
            it.etag = etag
            it.contentLength = contentLength
            it.quotaUsedBytes = quotaUsedBytes
            it.quotaAvailableBytes = quotaAvailableBytes
            it.lastModified = lastModified
        }
    }
    
    // ==================== Children Management API ====================
    // All operations below are O(1) thanks to LinkedHashMap
    
    /**
     * Returns the number of children. O(1).
     */
    val childCount: Int
        get() = childrenMap.size
    
    /**
     * Checks if this file has any children. O(1).
     */
    fun hasChildren(): Boolean = childrenMap.isNotEmpty()
    
    /**
     * Finds a child file by its path. O(1).
     * @param childPath The path of the child to find
     * @return The child file, or null if not found
     */
    fun findChildByPath(childPath: Path): WebDavFile? = childrenMap[childPath]
    
    /**
     * Checks if a child with the given path exists. O(1).
     */
    fun containsChild(childPath: Path): Boolean = childrenMap.containsKey(childPath)
    
    /**
     * Adds a child file. O(1).
     * If a child with the same path already exists, it will be replaced.
     * @param child The child file to add
     */
    fun addChild(child: WebDavFile) {
        childrenMap[child.path] = child
    }
    
    /**
     * Adds multiple children. O(n) where n is the number of children to add.
     */
    fun addChildren(children: Collection<WebDavFile>) {
        children.forEach { childrenMap[it.path] = it }
    }
    
    /**
     * Removes a child by reference. O(1).
     * @return true if the child was removed, false if not found
     */
    fun removeChild(child: WebDavFile): Boolean = childrenMap.remove(child.path) != null
    
    /**
     * Removes a child by path. O(1).
     * @return The removed child, or null if not found
     */
    fun removeChildByPath(childPath: Path): WebDavFile? = childrenMap.remove(childPath)
    
    /**
     * Removes all children. O(1).
     */
    fun clearChildren() {
        childrenMap.clear()
    }
    
    /**
     * Returns an iterator over the children in insertion order.
     * Modifications during iteration are supported via the iterator's remove() method.
     */
    fun childrenIterator(): MutableIterator<WebDavFile> = childrenMap.values.iterator()
    
    /**
     * Returns all children as a collection.
     * The returned collection is a view backed by the map; changes to the map
     * are reflected in the collection. For a snapshot, use [childrenSnapshot].
     */
    fun children(): Collection<WebDavFile> = childrenMap.values
    
    /**
     * Returns a snapshot (copy) of all children as a list.
     * Use this when you need to iterate while potentially modifying the children.
     */
    fun childrenSnapshot(): List<WebDavFile> = childrenMap.values.toList()
}
