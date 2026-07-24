package com.elenglish.studymentor.performance

import com.elenglish.studymentor.data.local.CacheMetadataDao
import com.elenglish.studymentor.data.local.CatalogDao
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Every database access must be suspending or a `Flow`.
 *
 * A blocking DAO method is the easy way to stall the main thread, and it is
 * easy to add one by accident. Reflection catches it at build time rather than
 * leaving it to be noticed as jank on a slow device.
 */
class MainThreadSafetyTest {

    private fun Method.isSuspending(): Boolean =
        parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"

    private fun Method.returnsFlow(): Boolean = Flow::class.java.isAssignableFrom(returnType)

    private fun assertAllOffMainThread(daoClass: Class<*>) {
        val offending = daoClass.declaredMethods
            .filterNot { it.isSynthetic }
            .filterNot { it.isSuspending() || it.returnsFlow() }
            .map { it.name }

        assertTrue(
            "${daoClass.simpleName} has blocking methods: $offending",
            offending.isEmpty(),
        )
    }

    @Test
    fun `catalog dao never exposes a blocking call`() {
        assertAllOffMainThread(CatalogDao::class.java)
    }

    @Test
    fun `cache metadata dao never exposes a blocking call`() {
        assertAllOffMainThread(CacheMetadataDao::class.java)
    }

    @Test
    fun `the dao surface is actually being inspected`() {
        // Guards the guard: a renamed or emptied DAO would otherwise pass
        // vacuously.
        assertTrue(CatalogDao::class.java.declaredMethods.size >= 10)
        assertTrue(CacheMetadataDao::class.java.declaredMethods.size >= 4)
    }
}
