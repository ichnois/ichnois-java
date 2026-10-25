package io.ichnois.spring.cache

import io.ichnois.IScopes
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.springframework.cache.CacheManager

class IchnoisCoreCacheBeanPostProcessorTest {

  private val scopes: IScopes = mock()

  @Test
  fun `wraps CacheManager beans in IchnoisCoreCacheManagerWrapper`() {
    val cacheManager = mock<CacheManager>()
    val processor = IchnoisCoreCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(cacheManager, "cacheManager")

    assertTrue(result is IchnoisCoreCacheManagerWrapper)
  }

  @Test
  fun `does not double-wrap IchnoisCoreCacheManagerWrapper`() {
    val delegate = mock<CacheManager>()
    val alreadyWrapped = IchnoisCoreCacheManagerWrapper(delegate, scopes)
    val processor = IchnoisCoreCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(alreadyWrapped, "cacheManager")

    assertSame(alreadyWrapped, result)
  }

  @Test
  fun `does not wrap non-CacheManager beans`() {
    val someBean = "not a cache manager"
    val processor = IchnoisCoreCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }
}
