package dev.pulsar.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ContactResolverTest {

    private lateinit var resolver: ContactResolver

    private fun findAssetFile(filename: String): File {
        val candidates = listOf(
            File("src/main/assets/$filename"),
            File("app/src/main/assets/$filename"),
            File("../../app/src/main/assets/$filename"),
            File("../app/src/main/assets/$filename"),
            File("d:/Coding/Pulsar/android/app/src/main/assets/$filename")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Asset $filename not found in any candidate path: $candidates")
    }

    @Before
    fun setUp() {
        val contactsJson = findAssetFile("contacts_100.json").readText(Charsets.UTF_8)
        val kinshipJson = findAssetFile("kinship_graph.json").readText(Charsets.UTF_8)
        resolver = ContactResolver(contactsJson, kinshipJson)
    }

    @Test
    fun testAmmuCall() {
        val res = resolver.resolveContact("আম্মুকে কল দে")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Ammu", res.contact?.name)
    }

    @Test
    fun testProstheticHesitationVowelOnBoss() {
        // The famous "আবসের" quirk from benchmark row 4
        val res = resolver.resolveContact("আবসের ইমেইলটা চেক করত")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Tareq Boss", res.contact?.name)
    }

    @Test
    fun testKinshipAmbiguityThreeSons() {
        // Elder says generic "ছেলেকে" without modifier
        val res = resolver.resolveContact("আমার ছেলেকে ফোন দাও")
        assertEquals(ResolveStatus.AMBIGUOUS, res.status)
        assertEquals(3, res.candidates.size)
        assertTrue(res.clarificationMessage?.contains("৩ জন") == true)
        assertTrue(res.clarificationMessage?.contains("বড় ছেলে") == true)
    }

    @Test
    fun testKinshipSpecificModifierChotoChele() {
        val res = resolver.resolveContact("আমার ছোট ছেলেকে কল কর")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Rifat Hasan", res.contact?.name)
        assertEquals("younger_son", res.relationRole)
    }

    @Test
    fun testKinshipSpecificModifierBoroChele() {
        val res = resolver.resolveContact("বড় ছেলেকে একটা ফোন লাগাও")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Tanvir Ahmed", res.contact?.name)
        assertEquals("elder_son", res.relationRole)
    }

    @Test
    fun testSpouseGinni() {
        val res = resolver.resolveContact("গিন্নিকে কল দাও")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Rokeya Begum", res.contact?.name)
    }

    @Test
    fun testStemmingAndFuzzyMatching() {
        // Suffix stripped: তানভীরকে -> তানভীর
        val res = resolver.resolveContact("তানভীরকে কল দে")
        assertEquals(ResolveStatus.RESOLVED, res.status)
        assertEquals("Tanvir Ahmed", res.contact?.name)
    }
}
