package com.stevenpg.opsmcp.tools;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * Prompts are user-invoked templates (they show up as slash commands in most
 * clients). This one encodes the triage order an on-call engineer would use,
 * so the model doesn't start with thread dumps when the database is down.
 */
@Component
public class OpsPrompts {

    @McpPrompt(name = "triage", title = "Triage this service",
            description = "Walk the ops tools in a sensible order to explain a reported symptom.")
    public String triage(@McpArg(name = "symptom", description = "What the user is seeing, e.g. 'orders API returning 500s'",
            required = true) String symptom) {
        return """
                A user reports: "%s"

                Investigate this service using the ops tools, in this order, and stop as soon as you have a
                well-supported explanation:

                1. get_health - is any component DOWN or OUT_OF_SERVICE?
                2. http_traffic_summary - which endpoint has the errors or the latency?
                3. recent_logs with minLevel=WARN - what do the errors say? Filter by the endpoint's logger if you can.
                4. jvm_summary / thread_summary - only if the above point at resource exhaustion or hangs.

                If you need more detail from a specific logger, you may use set_log_level_temporarily with DEBUG
                and a short TTL, then read recent_logs again. Say that you did this.

                Answer with: the most likely cause, the evidence (tool + value), and what you'd check next.
                Do not guess beyond the evidence.
                """.formatted(symptom);
    }
}
