package com.pdasilem.contactwork;

import static org.assertj.core.api.Assertions.assertThat;

import com.pdasilem.contactwork.ai.LocalAiService;
import com.pdasilem.contactwork.project.ProjectService;
import org.junit.jupiter.api.Test;

class ProductionConstructorPolicyTest {

    @Test
    void productionServicesExposeOnlySpringConstructor() {
        assertThat(LocalAiService.class.getDeclaredConstructors()).hasSize(1);
        assertThat(ProjectService.class.getDeclaredConstructors()).hasSize(1);
    }
}
