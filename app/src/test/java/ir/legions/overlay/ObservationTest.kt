package ir.legions.overlay
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ObservationTest {
    @Test fun changedPausedOrStoppedScreenCannotReuseAnAcceptedObservation() {
        try {
            CaptureRuntime.running=true; CaptureRuntime.paused=false
            CaptureRuntime.candidate=JSONObject(); CaptureRuntime.candidateSequence=7; CaptureRuntime.acceptedSequence=7
            assertTrue(CaptureRuntime.currentObservationIsAccepted())
            CaptureRuntime.candidateSequence=8
            assertFalse(CaptureRuntime.currentObservationIsAccepted())
            CaptureRuntime.acceptedSequence=8; CaptureRuntime.paused=true
            assertFalse(CaptureRuntime.currentObservationIsAccepted())
            CaptureRuntime.paused=false; CaptureRuntime.running=false
            assertFalse(CaptureRuntime.currentObservationIsAccepted())
            CaptureRuntime.running=true; CaptureRuntime.invalidate()
            assertFalse(CaptureRuntime.currentObservationIsAccepted())
        } finally { CaptureRuntime.running=false; CaptureRuntime.paused=false; CaptureRuntime.invalidate() }
    }
}
