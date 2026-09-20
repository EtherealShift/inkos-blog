package com.inkos.admin.cache;

import com.inkos.common.cache.CacheService;
import com.inkos.content.cache.ContentCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentCacheTransactionTest {
    @Test
    @SuppressWarnings("unchecked")
    void invalidationWaitsUntilCommit() {
        CacheService backend = mock(CacheService.class);
        ObjectProvider<CacheService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(backend);
        ContentCache cache = new ContentCache(provider);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.evictArticleDetail("test-slug");
            cache.invalidateArticleLists();
            verifyNoInteractions(backend);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(backend).evict(cache.articleDetailKey("test-slug"));
            verify(backend).increment(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
