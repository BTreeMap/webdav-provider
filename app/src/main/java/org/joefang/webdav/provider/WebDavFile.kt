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
 * This class maintains a tree structure of files where directories
 * can have children. For efficient lookup operations, children are
 * stored both in a list (for ordered iteration) and a HashMap 
 * (for O(1) path-based lookup).
 */
class WebDavFile(
    var path: Path,
    var isDirectory: Boolean = false,
    var contentType: String? = null,
    var isPending: Boolean = false
) {
    var parent: WebDavFile? = null
    
    // Use a backing list for ordered iteration and a HashMap for O(1) lookup
    private val childrenList: MutableList<WebDavFile> = ArrayList()
    private val childrenByPath: MutableMap<Path, WebDavFile> = HashMap()
    
    /**
     * Returns a mutable list view of children for backward compatibility.
     * For better performance when looking up by path, use [findChildByPath].
     */
    val children: MutableList<WebDavFile>
        get() = ChildrenListWrapper(childrenList, childrenByPath)
    
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
    
    /**
     * Finds a child file by its path in O(1) time.
     * @param childPath The path of the child to find
     * @return The child file, or null if not found
     */
    fun findChildByPath(childPath: Path): WebDavFile? {
        return childrenByPath[childPath]
    }
    
    /**
     * A wrapper around the children list that keeps the HashMap in sync.
     * This ensures backward compatibility with existing code that uses
     * children as a MutableList while providing O(1) lookup by path.
     */
    private class ChildrenListWrapper(
        private val backingList: MutableList<WebDavFile>,
        private val pathIndex: MutableMap<Path, WebDavFile>
    ) : MutableList<WebDavFile> by backingList {
        
        override fun add(element: WebDavFile): Boolean {
            pathIndex[element.path] = element
            return backingList.add(element)
        }
        
        override fun add(index: Int, element: WebDavFile) {
            pathIndex[element.path] = element
            backingList.add(index, element)
        }
        
        override fun addAll(elements: Collection<WebDavFile>): Boolean {
            elements.forEach { pathIndex[it.path] = it }
            return backingList.addAll(elements)
        }
        
        override fun addAll(index: Int, elements: Collection<WebDavFile>): Boolean {
            elements.forEach { pathIndex[it.path] = it }
            return backingList.addAll(index, elements)
        }
        
        override fun remove(element: WebDavFile): Boolean {
            pathIndex.remove(element.path)
            return backingList.remove(element)
        }
        
        override fun removeAt(index: Int): WebDavFile {
            val element = backingList.removeAt(index)
            pathIndex.remove(element.path)
            return element
        }
        
        override fun removeAll(elements: Collection<WebDavFile>): Boolean {
            elements.forEach { pathIndex.remove(it.path) }
            return backingList.removeAll(elements)
        }
        
        override fun retainAll(elements: Collection<WebDavFile>): Boolean {
            val pathsToKeep = elements.map { it.path }.toSet()
            pathIndex.keys.retainAll(pathsToKeep)
            return backingList.retainAll(elements)
        }
        
        override fun clear() {
            pathIndex.clear()
            backingList.clear()
        }
        
        override fun set(index: Int, element: WebDavFile): WebDavFile {
            val old = backingList[index]
            pathIndex.remove(old.path)
            pathIndex[element.path] = element
            return backingList.set(index, element)
        }
        
        override fun iterator(): MutableIterator<WebDavFile> {
            return IndexSyncIterator(backingList.iterator(), pathIndex)
        }
        
        override fun listIterator(): MutableListIterator<WebDavFile> {
            return IndexSyncListIterator(backingList.listIterator(), pathIndex)
        }
        
        override fun listIterator(index: Int): MutableListIterator<WebDavFile> {
            return IndexSyncListIterator(backingList.listIterator(index), pathIndex)
        }
        
        private class IndexSyncIterator(
            private val delegate: MutableIterator<WebDavFile>,
            private val pathIndex: MutableMap<Path, WebDavFile>
        ) : MutableIterator<WebDavFile> {
            private var current: WebDavFile? = null
            
            override fun hasNext(): Boolean = delegate.hasNext()
            override fun next(): WebDavFile {
                current = delegate.next()
                return current!!
            }
            override fun remove() {
                current?.let { pathIndex.remove(it.path) }
                delegate.remove()
            }
        }
        
        private class IndexSyncListIterator(
            private val delegate: MutableListIterator<WebDavFile>,
            private val pathIndex: MutableMap<Path, WebDavFile>
        ) : MutableListIterator<WebDavFile> {
            private var current: WebDavFile? = null
            
            override fun hasNext(): Boolean = delegate.hasNext()
            override fun hasPrevious(): Boolean = delegate.hasPrevious()
            override fun next(): WebDavFile {
                current = delegate.next()
                return current!!
            }
            override fun nextIndex(): Int = delegate.nextIndex()
            override fun previous(): WebDavFile {
                current = delegate.previous()
                return current!!
            }
            override fun previousIndex(): Int = delegate.previousIndex()
            override fun add(element: WebDavFile) {
                pathIndex[element.path] = element
                delegate.add(element)
            }
            override fun remove() {
                current?.let { pathIndex.remove(it.path) }
                delegate.remove()
            }
            override fun set(element: WebDavFile) {
                current?.let { pathIndex.remove(it.path) }
                pathIndex[element.path] = element
                delegate.set(element)
            }
        }
    }
}
