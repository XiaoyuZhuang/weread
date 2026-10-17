package com.xiaoyu.weread

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqRetryPolicyTest {
    @Test fun retriesMissingToolOnBadRequest() {
        assertTrue(GroqRetryPolicy.isMissingRequiredTool(
            400, "Tool choice is required but model didn't call a tool"
        ))
        assertTrue(GroqRetryPolicy.isMissingRequiredTool(
            400, "Tool choice required: model did not call a tool"
        ))
    }

    @Test fun neverRetriesInvalidPayload() {
        assertFalse(GroqRetryPolicy.isMissingRequiredTool(400, "Invalid tool type"))
        assertFalse(GroqRetryPolicy.isMissingRequiredTool(400, "Missing required model"))
    }

    @Test fun neverRetriesAuthenticationOrQuota() {
        assertFalse(GroqRetryPolicy.isMissingRequiredTool(401, "Tool choice is required but model didn't call a tool"))
        assertFalse(GroqRetryPolicy.isMissingRequiredTool(403, "Tool choice is required but model didn't call a tool"))
        assertFalse(GroqRetryPolicy.isMissingRequiredTool(429, "Tool choice is required but model didn't call a tool"))
    }
}
