package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test
class AdvisorValidatorTest {
    private val validator=AdvisorValidator()
    @Test fun requiresExactlySixAndDistinctDirectStrategies() {
        val valid=AdvisorFixtures.valid(); assertTrue(validator.validate(valid).valid)
        assertFalse(validator.validate(valid.copy(replies=valid.replies.take(5))).valid)
        assertFalse(validator.validate(valid.copy(replies=valid.replies+valid.replies.first())).valid)
        assertFalse(validator.validate(valid.copy(replies=valid.replies.map { it.copy(strategy=Strategy.ACKNOWLEDGE) })).valid)
    }
    @Test fun duplicateWhitespaceAndPunctuationAreRejected() {
        val b=AdvisorFixtures.valid(); val replies=b.replies.toMutableList(); replies[1]=replies[1].copy(text=" "+replies[0].text.replace("？", "? "))
        assertFalse(validator.validate(b.copy(replies=replies)).valid)
    }
    @Test fun labelOnlyAndMissingFieldsAreRejected() {
        val b=AdvisorFixtures.valid()
        assertFalse(validator.validate(b.copy(replies=b.replies.map { it.copy(reason="恋爱大师") })).valid)
        assertFalse(validator.validate(b.copy(replies=b.replies.map { it.copy(suitableWhen="") })).valid)
    }
    @Test fun riskScoresAndLengthsAreBounded() {
        val b=AdvisorFixtures.valid(); assertFalse(validator.validate(b.copy(analysis=b.analysis.copy(risk=10))).valid)
        assertFalse(validator.validate(b.copy(replies=b.replies.map { it.copy(score=Double.NaN) })).valid)
        assertFalse(validator.validate(b.copy(replies=b.replies.map { it.copy(text="字".repeat(301)) })).valid)
    }
    @Test fun fencedJsonParsesAndMalformedFails() {
        val codec=AdvisorCodec(); val parsed=codec.parse("```json\n"+AdvisorFixtures.json()+"\n```")
        assertEquals(6,parsed?.replies?.size); assertTrue(validator.validate(parsed!!).valid)
        assertNull(codec.parse("not JSON"))
    }
}
