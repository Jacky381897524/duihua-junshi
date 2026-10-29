package com.loveadvisor.core.memory

import com.loveadvisor.core.advisor.AdvisorFixtures
import org.junit.Assert.*
import org.junit.Test

class InteractionPresentationTest {
    @Test fun noEvidenceDoesNotInventAnAffectionMeasurement() {
        val shown=InteractionPresenter.build(RealityMemoryState(),AdvisorFixtures.snapshot())
        assertNull(shown.estimate);assertEquals(0,shown.evidenceCount)
    }
    @Test fun contactSwitchDoesNotShowAnotherContactsEstimate() {
        val a=AdvisorFixtures.snapshot();val b=a.copy(identity=a.identity!!.copy(displayTitle="另一个对象"))
        val id=PersonalIdentity.root(a)!!
        val state=RealityMemoryState(data=PersonalData(signals=listOf(InteractionSignal("e",id,"closeness",123,1.5))))
        assertEquals(51.5,InteractionPresenter.build(state,a).estimate!!,.001)
        assertNull(InteractionPresenter.build(state,b).estimate)
    }
}
