package com.mammoth.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Protocol framing checks: responses must be newline-delimited JSON, UTF-8 encoded (Chinese payloads
 * must survive), and tool calls without a configured agent identity must fail closed.
 */
class McpServerMainTest {

  private static String run(String input, String agentUserId) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    BufferedReader in = new BufferedReader(new StringReader(input));
    McpServerMain.serve(in, out, mock(McpToolService.class), agentUserId);
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void initializeAndToolsListAreUtf8JsonLines() throws Exception {
    String output = run(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}\n"
            + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n",
        "agent-1");

    String[] lines = output.strip().split("\n");
    assertThat(lines).hasSize(2);
    assertThat(lines[0]).contains("\"protocolVersion\":\"2024-11-05\"");
    assertThat(lines[1]).contains("search_project_knowledge");
    assertThat(lines[1]).contains("get_knowledge_document");
    assertThat(lines[1]).contains("get_requirement_context");
  }

  @Test
  void refusesToolCallsWithoutAgentIdentity() throws Exception {
    String output = run(
        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"search_project_knowledge\",\"arguments\":{\"projectId\":\"p\",\"query\":\"q\"}}}\n",
        "");

    assertThat(output).contains("\"isError\":true");
    assertThat(output).contains("MAMMOTH_MCP_AGENT_USER_ID");
  }

  @Test
  void sendsMethodNotFoundForUnknownRequests() throws Exception {
    String output = run("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"nope\"}\n", "agent-1");
    assertThat(output).contains("\"code\":-32601");
  }
}
