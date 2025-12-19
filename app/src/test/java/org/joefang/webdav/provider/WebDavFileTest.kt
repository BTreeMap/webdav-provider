package org.joefang.webdav.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Paths

class WebDavFileTest {

    @Test
    fun `children list add and retrieve works correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        
        assertEquals(2, parent.children.size)
        assertTrue(parent.children.contains(child1))
        assertTrue(parent.children.contains(child2))
    }

    @Test
    fun `findChildByPath returns child in O(1) time`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        val child3 = WebDavFile(Paths.get("/documents/file3.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        parent.children.add(child3)
        
        // Find using O(1) HashMap lookup
        val found = parent.findChildByPath(Paths.get("/documents/file2.txt"))
        
        assertNotNull(found)
        assertEquals(child2, found)
    }

    @Test
    fun `findChildByPath returns null for non-existent path`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child = WebDavFile(Paths.get("/documents/file.txt"))
        
        parent.children.add(child)
        
        val found = parent.findChildByPath(Paths.get("/documents/nonexistent.txt"))
        
        assertNull(found)
    }

    @Test
    fun `children remove updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        
        // Remove child1
        parent.children.remove(child1)
        
        // Verify removal from both list and HashMap
        assertEquals(1, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }

    @Test
    fun `children clear removes all from HashMap`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        
        parent.children.clear()
        
        assertEquals(0, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }

    @Test
    fun `children addAll updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val children = listOf(
            WebDavFile(Paths.get("/documents/file1.txt")),
            WebDavFile(Paths.get("/documents/file2.txt")),
            WebDavFile(Paths.get("/documents/file3.txt"))
        )
        
        parent.children.addAll(children)
        
        assertEquals(3, parent.children.size)
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file3.txt")))
    }

    @Test
    fun `children removeAll updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        val child3 = WebDavFile(Paths.get("/documents/file3.txt"))
        
        parent.children.addAll(listOf(child1, child2, child3))
        parent.children.removeAll(listOf(child1, child3))
        
        assertEquals(1, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
        assertNull(parent.findChildByPath(Paths.get("/documents/file3.txt")))
    }

    @Test
    fun `children set updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        
        // Replace child1 with child2
        parent.children[0] = child2
        
        assertEquals(1, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }

    @Test
    fun `children iterator remove updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        
        val iterator = parent.children.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file.path == Paths.get("/documents/file1.txt")) {
                iterator.remove()
            }
        }
        
        assertEquals(1, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }

    @Test
    fun `children removeAt updates HashMap correctly`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        val child1 = WebDavFile(Paths.get("/documents/file1.txt"))
        val child2 = WebDavFile(Paths.get("/documents/file2.txt"))
        
        parent.children.add(child1)
        parent.children.add(child2)
        
        parent.children.removeAt(0)
        
        assertEquals(1, parent.children.size)
        assertNull(parent.findChildByPath(Paths.get("/documents/file1.txt")))
        assertNotNull(parent.findChildByPath(Paths.get("/documents/file2.txt")))
    }

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

    @Test
    fun `performance test for findChildByPath with many children`() {
        val parent = WebDavFile(Paths.get("/documents"), isDirectory = true)
        
        // Add 1000 children
        repeat(1000) { i ->
            parent.children.add(WebDavFile(Paths.get("/documents/file$i.txt")))
        }
        
        // Find the 500th child - should be O(1)
        val startTime = System.nanoTime()
        val found = parent.findChildByPath(Paths.get("/documents/file500.txt"))
        val endTime = System.nanoTime()
        
        assertNotNull(found)
        assertEquals(Paths.get("/documents/file500.txt"), found?.path)
        
        // HashMap lookup should be very fast (under 1ms)
        val durationMs = (endTime - startTime) / 1_000_000
        assertTrue("HashMap lookup took too long: ${durationMs}ms", durationMs < 10)
    }
}
