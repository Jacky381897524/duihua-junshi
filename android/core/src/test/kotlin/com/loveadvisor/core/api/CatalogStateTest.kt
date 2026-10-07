package com.loveadvisor.core.api
import org.junit.Assert.*
import org.junit.Test
class CatalogStateTest {
    @Test fun editedProfileRejectsOldResult() {
        val state=CatalogState("old"); assertFalse(state.apply(CatalogResult(1,listOf(ApiModel("wrong"))),2)); assertEquals("old",state.selectedModelId); assertTrue(state.models.isEmpty())
    }
    @Test fun failureKeepsSuccessfulSelection() {
        val state=CatalogState(); state.apply(CatalogResult(3,listOf(ApiModel("real","展示名"))),3)
        assertTrue(state.select("real")); assertFalse(state.apply(CatalogResult(3,error="401"),3)); assertEquals("real",state.selectedModelId); assertEquals(1,state.models.size)
    }
    @Test fun displayNameNeverBecomesId() {
        val state=CatalogState(); assertTrue(state.apply(CatalogResult(4,listOf(ApiModel("id-1","名字"))),4))
        assertFalse(state.select("名字")); assertTrue(state.select("id-1")); assertEquals("id-1",state.selectedModelId); assertTrue(state.canSave())
    }
    @Test fun invalidatePreventsUsingOldDirectory() {
        val state=CatalogState(); state.apply(CatalogResult(1,listOf(ApiModel("a"))),1); state.select("a"); state.invalidate()
        assertFalse(state.canSave()); assertTrue(state.models.isEmpty())
        assertTrue(state.manual("custom-model")); assertTrue(state.canSave())
    }
    @Test fun emptySelectionCannotSave() { assertFalse(CatalogState().canSave()) }
}
