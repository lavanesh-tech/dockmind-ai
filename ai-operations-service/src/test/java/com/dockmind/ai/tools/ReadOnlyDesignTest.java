package com.dockmind.ai.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the design, so a future change can't quietly give the AI write access: the backend
 * client has exactly one public operation, a GET, and the tools are exactly the listed reads.
 */
class ReadOnlyDesignTest {

    @Test
    void theBackendClientCanOnlyGet() {
        List<String> publicMethods = Arrays.stream(ReadOnlyBackend.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();
        assertThat(publicMethods).containsExactly("get");
    }

    @Test
    void toolsAreExactlyTheDocumentedReads() {
        List<String> tools = Arrays.stream(OperationsTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .map(Method::getName)
                .toList();
        assertThat(tools).containsExactlyInAnyOrderElementsOf(OperationsTools.NAMES);
        assertThat(tools).allMatch(n -> n.startsWith("get") || n.startsWith("list"));
    }
}
