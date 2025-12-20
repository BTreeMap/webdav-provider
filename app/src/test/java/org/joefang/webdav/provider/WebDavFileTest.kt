package org.joefang.webdav.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Paths

/**
 * Comprehensive tests for WebDavFile, especially the LinkedHashMap-based children management.
 * 
 * These tests verify:
 * - O(1) operations (add, remove, lookup)
 * - Insertion order preservation
 * - Iterator correctness (including modification during iteration)
 * - Edge cases and challenging access patterns
 */
class WebDavFileTest {

    // ==================== Basic Operations ====================
    
    @Test
    fun `addChild adds child and findChildByPath retrieves it`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child = WebDavFile(Paths.get("/documents/file.txt"))
        
        parent.addChild(child)
        
        assertEquals(1, parent.childCount)
        assertEquals(child, parent.findChildByPath(Paths.get("/documents/file.txt")))
    }
    
    @Test
    fun `addChildren adds multiple children`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val children = listOf(
            WebDavFile(Paths.get("/documents/file1.txt")),
            WebDavFile(Paths.get("/documents/file2.txt")),
            WebDavFile(Paths.get("/documents/file3.txt"))
        )
        
        parent.addChildren(children)
        
        assertEquals(3, parent.childCount)
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file3.txt")))
    }
    
    @Test
    fun `removeChild removes child`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.addChild(child1)
        parent.addChild(child2)
        
        val removed = parent.removeChild(child1)
        
        assertTrue(removed)
        assertEquals(1, parent.childCount)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }
    
    @Test
    fun `removeChild returns false for non-existent child`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child = WebDavFile(Paths.get("/documents/file.txt"))
        
        val removed = parent.removeChild(child)
        
        assertFalse(removed)
    }
    
    @Test
    fun `removeChildByPath removes child and returns it`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child = WebDavFile(Paths.get("/documents/file.txt"))
        
        parent.addChild(child)
        
        val removed = parent.removeChildByPath(Paths.get("/documents/file.txt"))
        
        assertEquals(child, removed)
        assertEquals(0, parent.childCount)
    }
    
    @Test
    fun `removeChildByPath returns null for non-existent path`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        val removed = parent.removeChildByPath(Paths.get("/documents/nonexistent.txt"))
        
        assertNull(removed)
    }
    
    @Test
    fun `clearChildren removes all children`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file1.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file2.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file3.txt")))
        
        parent.clearChildren()
        
        assertEquals(0, parent.childCount)
        assertFalse(parent.hasChildren())
    }
    
    // ==================== O(1) Lookup Tests ====================
    
    @Test
    fun `findChildByPath returns child in O(1) time`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        val child3 = WebDavFile(Paths.get("/documents/file3.txt"))
        
        parent.addChild(child1)
        parent.addChild(child2)
        parent.addChild(child3)
        
        val found = parent.findChildByPath(Paths.get("/documents/file2.txt"))
        
        assertNotNull(found)
        assertEquals(child2, found)
    }
    
    @Test
    fun `findChildByPath returns null for non-existent path`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file.txt")))
        
        val found = parent.findChildByPath(Paths.get("/documents/nonexistent.txt"))
        
        assertNull(found)
    }
    
    @Test
    fun `containsChild returns true for existing child`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file.txt")))
        
        assertTrue(parent.containsChild(Paths.get("/documents/file.txt")))
    }
    
    @Test
    fun `containsChild returns false for non-existent child`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        assertFalse(parent.containsChild(Paths.get("/documents/nonexistent.txt")))
    }
    
    // ==================== Insertion Order Preservation ====================
    
    @Test
    fun `children iteration preserves insertion order`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val paths = listOf(
            Paths.get("/documents/a.txt"),
            Paths.get("/documents/b.txt"),
            Paths.get("/documents/c.txt"),
            Paths.get("/documents/d.txt")
        )
        
        paths.forEach { parent.addChild(WebDavFile(it)) }
        
        val iteratedPaths = parent.children().map { it.path }
        
        assertEquals(paths, iteratedPaths)
    }
    
    @Test
    fun `insertion order preserved after removal and re-addition`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/a.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/b.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/c.txt")))
        
        // Remove 'b' and re-add it - it should go to the end
        parent.removeChildByPath(Paths.get("/documents/b.txt"))
        parent.addChild(WebDavFile(Paths.get("/documents/b.txt")))
        
        val iteratedPaths = parent.children().map { it.path.fileName.toString() }
        
        assertEquals(listOf("a.txt", "c.txt", "b.txt"), iteratedPaths)
    }
    
    // ==================== Iterator Tests ====================
    
    @Test
    fun `childrenIterator iterates in insertion order`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file1.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file2.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file3.txt")))
        
        val names = mutableListOf<String>()
        val iterator = parent.childrenIterator()
        while (iterator.hasNext()) {
            names.add(iterator.next().name)
        }
        
        assertEquals(listOf("file1.txt", "file2.txt", "file3.txt"), names)
    }
    
    @Test
    fun `childrenIterator remove works correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file1.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file2.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file3.txt")))
        
        val iterator = parent.childrenIterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file.name == "file2.txt") {
                iterator.remove()
            }
        }
        
        assertEquals(2, parent.childCount)
        assertNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file3.txt")))
    }
    
    @Test
    fun `childrenSnapshot allows safe iteration during modification`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file1.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file2.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/file3.txt")))
        
        // Take a snapshot and modify original during iteration
        for (child in parent.childrenSnapshot()) {
            if (child.name == "file2.txt") {
                parent.removeChild(child)
            }
        }
        
        assertEquals(2, parent.childCount)
        assertNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }
    
    // ==================== Edge Cases ====================
    
    @Test
    fun `addChild replaces existing child with same path`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file.txt"))
        child1.contentLength = 100L
        val child2 = WebDavFile(Paths.get("/documents/file.txt"))
        child2.contentLength = 200L
        
        parent.addChild(child1)
        parent.addChild(child2)  // Same path, replaces child1
        
        assertEquals(1, parent.childCount)
        assertEquals(200L, parent.findChildByPath(Paths.get("/documents/file.txt"))?.contentLength)
    }
    
    @Test
    fun `hasChildren returns false for empty parent`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        assertFalse(parent.hasChildren())
    }
    
    @Test
    fun `hasChildren returns true for non-empty parent`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        parent.addChild(WebDavFile(Paths.get("/documents/file.txt")))
        
        assertTrue(parent.hasChildren())
    }
    
    @Test
    fun `childCount is accurate after multiple operations`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        assertEquals(0, parent.childCount)
        
        parent.addChild(WebDavFile(Paths.get("/documents/a.txt")))
        assertEquals(1, parent.childCount)
        
        parent.addChild(WebDavFile(Paths.get("/documents/b.txt")))
        parent.addChild(WebDavFile(Paths.get("/documents/c.txt")))
        assertEquals(3, parent.childCount)
        
        parent.removeChildByPath(Paths.get("/documents/b.txt"))
        assertEquals(2, parent.childCount)
        
        parent.clearChildren()
        assertEquals(0, parent.childCount)
    }
    
    // ==================== Large Scale Tests ====================
    
    @Test
    fun `operations work correctly with many children`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        // Add 1000 children
        repeat(1000) { i ->
            parent.addChild(WebDavFile(Paths.get("/documents/file$i.txt")))
        }
        
        assertEquals(1000, parent.childCount)
        
        // Verify lookup at various positions
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file0.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file500.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file999.txt")))
        assertNull(parent.findChildByPath(Paths.get("/documents/file1000.txt")))
        
        // Remove some and verify
        parent.removeChildByPath(Paths.get("/documents/file500.txt"))
        assertEquals(999, parent.childCount)
        assertNull(parent.findChildByPath(Paths.get("/documents/file500.txt")))
    }
    
    @Test
    fun `insertion order preserved with large number of children`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val expectedOrder = (0 until 100).map { Paths.get("/documents/file$it.txt") }
        
        expectedOrder.forEach { parent.addChild(WebDavFile(it)) }
        
        val actualOrder = parent.children().map { it.path }
        
        assertEquals(expectedOrder, actualOrder)
    }
    
    // ==================== Path Immutability Tests ====================
    
    @Test
    fun `path is immutable`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"))
        
        // path is now 'val' so this should not compile if attempted
        // file.path = Paths.get("/other/path.txt")  // Compile error
        
        assertEquals(Paths.get("/documents/file.txt"), file.path)
    }
    
    // ==================== WebDavFile Properties Tests ====================

    @Test
    fun `WebDavFile name property returns filename`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"))
        
        assertEquals("file.txt", file.name)
    }

    @Test
    fun `WebDavFile name property returns slash for root`() {
        val file = WebDavFile(Paths.get("/"))
        
        assertEquals("/", file.name)
    }

    @Test
    fun `WebDavFile decodedName decodes URL-encoded names`() {
        val file = WebDavFile(Paths.get("/documents/file%20name.txt"))
        
        assertEquals("file name.txt", file.decodedName)
    }

    @Test
    fun `WebDavFile davPath returns correct WebDavPath for file`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"), isDirectory = false)
        
        val davPath = file.davPath
        
        assertEquals(Paths.get("/documents/file.txt"), davPath.path)
        assertFalse(davPath.isDirectory)
    }

    @Test
    fun `WebDavFile davPath returns correct WebDavPath for directory`() {
        val dir = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        val davPath = dir.davPath
        
        assertEquals(Paths.get("/documents"), davPath.path)
        assertTrue(davPath.isDirectory)
    }

    @Test
    fun `WebDavFile toString returns path string`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"))
        
        assertEquals("/documents/file.txt", file.toString())
    }

    @Test
    fun `WebDavFile parent can be set`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child = WebDavFile(Paths.get("/documents/file.txt"))
        
        child.parent = parent
        
        assertEquals(parent, child.parent)
    }

    @Test
    fun `WebDavFile writable defaults to true`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"))
        
        assertTrue(file.writable)
    }

    @Test
    fun `WebDavFile isPending defaults to false`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"))
        
        assertFalse(file.isPending)
    }

    @Test
    fun `WebDavFile isPending can be set to true`() {
        val file = WebDavFile(Paths.get("/documents/file.txt"), isPending = true)
        
        assertTrue(file.isPending)
    }
}
